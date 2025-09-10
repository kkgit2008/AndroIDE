/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.lifecycle.Lifecycle
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.itsaky.androidide.R
import com.itsaky.androidide.logging.LifecycleAwareAppender
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.min

/**
 * Fragment to show IDE logs.
 * @author Akash Yadav
 */
class IDELogFragment : LogViewFragment() {

  // 日志收集器：全局唯一实例，避免重复创建
  private val lifecycleAwareAppender = LifecycleAwareAppender(Lifecycle.State.CREATED)
  // 线程安全数据源：独立管理日志数据
  private val threadSafeLogLines = CopyOnWriteArrayList<String>()
  // 公平锁：解决并发冲突，支持超时释放
  private val logLock = ReentrantLock(true)
  // 日志最大条数：平衡内存占用与日志完整性
  private val MAX_LOG_LINES = 1000
  // 主线程Handler：确保UI操作安全
  private val mainHandler = Handler(Looper.getMainLooper())
  // 日志上下文缓存：避免重复获取，减少性能损耗
  private lateinit var loggerContext: LoggerContext
  // 根日志器缓存：全局唯一，避免重复查找
  private lateinit var rootLogger: Logger

  override fun isSimpleFormattingEnabled() = true
  override fun getFilename() = "ide_logs"

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emptyStateViewModel.emptyMessage.value = getString(R.string.msg_emptyview_idelogs)

    // 1. 初始化日志上下文与根日志器（缓存，避免重复获取）
    loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    // 2. 配置日志收集器（仅初始化一次，避免重复绑定）
    lifecycleAwareAppender.consumer = this::safeConsumeLogLine // 绑定日志输出逻辑
    lifecycleAwareAppender.context = loggerContext // 绑定日志上下文
    lifecycleAwareAppender.attachTo(viewLifecycleOwner) // 绑定生命周期

    // 3. 启动日志收集并添加到根日志器（确保仅添加一次）
    if (!lifecycleAwareAppender.isStarted) {
      lifecycleAwareAppender.start()
    }
    if (!rootLogger.isAttached(lifecycleAwareAppender)) {
      rootLogger.addAppender(lifecycleAwareAppender)
    }
  }

  /**
   * 安全日志输出：处理并发写入、内存限制，确保日志正常传递
   */
  private fun safeConsumeLogLine(logLine: String) {
    // 锁超时保护，避免死锁
    if (!logLock.tryLock(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
      return
    }

    try {
      // 日志数量超限处理：批量移除最旧日志，避免内存溢出
      if (threadSafeLogLines.size >= MAX_LOG_LINES) {
        val removeCount = min(100, threadSafeLogLines.size)
        repeat(removeCount) { threadSafeLogLines.removeFirst() }
        // 主线程同步更新UI（清空旧日志后重新写入）
        mainHandler.post {
          if (isAdded && view != null) {
            super.clearOutput()
            threadSafeLogLines.forEach { super.appendLine(it) }
          }
        }
      }

      // 步骤1：更新数据源（线程安全）
      threadSafeLogLines.add(logLine)
      // 步骤2：主线程更新UI（确保视图操作安全）
      mainHandler.post {
        if (isAdded && view != null) {
          super.appendLine(logLine)
          // 同步更新空状态
          if (threadSafeLogLines.size == 1) {
            emptyStateViewModel.isEmpty.value = false
          }
        }
      }
    } finally {
      // 确保锁释放，避免死锁
      if (logLock.isHeldByCurrentThread) {
        logLock.unlock()
      }
    }
  }

    /**
     * 修复版清空方法：确保清空后日志收集正常恢复
     */
    // 替代方案：无pause/resume时，通过解绑/绑定consumer实现暂停/恢复
    override fun clearOutput() {
      mainHandler.post {
        if (!isAdded || view == null) return@post
        if (!logLock.tryLock(200, java.util.concurrent.TimeUnit.MILLISECONDS)) return@post
    
        try {
          // 步骤1：临时解绑consumer（暂停日志输出）
          lifecycleAwareAppender.consumer = null
    
          // 步骤2：清空数据源与UI（原有逻辑）
          threadSafeLogLines.clear()
          super.clearOutput()
          emptyStateViewModel.isEmpty.value = true
    
          // 步骤3：恢复consumer（恢复日志输出）
          lifecycleAwareAppender.consumer = this::safeConsumeLogLine
    
          // 兜底：确保Appender正常运行
          if (!lifecycleAwareAppender.isStarted) {
            lifecycleAwareAppender.start()
            if (!rootLogger.isAttached(lifecycleAwareAppender)) {
              rootLogger.addAppender(lifecycleAwareAppender)
            }
          }
        } finally {
          if (logLock.isHeldByCurrentThread) logLock.unlock()
        }
      }
    }

  /**
   * 检查Appender是否已附着到日志器（避免重复添加）
   * 修复：使用Logback提供的迭代器方法替代不存在的appendersList
   */
  private fun Logger.isAttached(appender: LifecycleAwareAppender): Boolean {
    val iterator = this.iteratorForAppenders()
    while (iterator.hasNext()) {
      if (iterator.next() == appender) {
        return true
      }
    }
    return false
  }

  override fun onDestroy() {
    super.onDestroy()
    // 彻底清理资源，避免内存泄漏
    if (lifecycleAwareAppender.isStarted) {
      lifecycleAwareAppender.stop()
    }
    if (::rootLogger.isInitialized && rootLogger.isAttached(lifecycleAwareAppender)) {
      rootLogger.detachAppender(lifecycleAwareAppender)
    }
    // 释放其他资源
    if (logLock.isHeldByCurrentThread) {
      logLock.unlock()
    }
    threadSafeLogLines.clear()
    mainHandler.removeCallbacksAndMessages(null)
  }

  /**
   * 安全获取当前日志列表（用于分享/导出）
   */
  fun getCurrentLogs(): List<String> {
    if (!logLock.tryLock(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
      return emptyList()
    }
    try {
      return threadSafeLogLines.toList()
    } finally {
      if (logLock.isHeldByCurrentThread) {
        logLock.unlock()
      }
    }
  }
}
