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

package com.itsaky.androidide.ui

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import androidx.annotation.GravityInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.transition.TransitionManager
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.SizeUtils
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialSharedAxis
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.EditorBottomSheetTabAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.databinding.LayoutEditorBottomSheetBinding
import com.itsaky.androidide.fragments.output.ShareableOutputFragment
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.tasks.TaskExecutor.CallbackWithError
import com.itsaky.androidide.tasks.TaskExecutor.executeAsync
import com.itsaky.androidide.tasks.TaskExecutor.executeAsyncProvideError
import com.itsaky.androidide.utils.IntentUtils.shareFile
import com.itsaky.androidide.utils.Symbols.forFile
import com.itsaky.androidide.utils.flashError
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import kotlin.math.roundToInt

/**
 * Bottom sheet shown in editor activity.
 * @author Akash Yadav
 */
class EditorBottomSheet
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes) {

  // 新增：日志相关变量
  private val logTag = "EditorBottomSheet"
  private val logFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
  private val logFile by lazy {
    File(context.filesDir, "editor_bottom_sheet_logs.txt")
  }

  private val collapsedHeight: Float by lazy {
    val localContext = getContext() ?: return@lazy 0f
    localContext.resources.getDimension(R.dimen.editor_sheet_collapsed_height)
  }
  private val behavior: BottomSheetBehavior<EditorBottomSheet> by lazy {
    BottomSheetBehavior.from(this).apply {
      isFitToContents = false
      skipCollapsed = true
    }
  }

  @JvmField
  var binding: LayoutEditorBottomSheetBinding
  val pagerAdapter: EditorBottomSheetTabAdapter

  private var anchorOffset = 0
  private var isImeVisible = false
  private var windowInsets: Insets? = null

  private val insetBottom: Int
    get() = if (isImeVisible) 0 else windowInsets?.bottom ?: 0

  companion object {

    private val log = LoggerFactory.getLogger(EditorBottomSheet::class.java)
    private const val COLLAPSE_HEADER_AT_OFFSET = 0.5f

    const val CHILD_HEADER = 0
    const val CHILD_SYMBOL_INPUT = 1
    const val CHILD_ACTION = 2
  }

  private fun initialize(context: FragmentActivity) {
    // 新增：记录初始化日志
    logToFile("Initializing EditorBottomSheet")

    val mediator =
      TabLayoutMediator(binding.tabs, binding.pager, true, true) { tab, position ->
        tab.text = pagerAdapter.getTitle(position)
      }

    mediator.attach()
    binding.pager.isUserInputEnabled = false
    binding.pager.offscreenPageLimit = pagerAdapter.itemCount - 1 // Do not remove any views

    binding.tabs.addOnTabSelectedListener(
      object : OnTabSelectedListener {
        override fun onTabSelected(tab: Tab) {
          // 新增：记录标签选择日志
          logToFile("Tab selected: position=${tab.position}, title=${tab.text}")
          
          val fragment: Fragment = pagerAdapter.getFragmentAtIndex(tab.position)
          if (fragment is ShareableOutputFragment) {
            binding.clearFab.show()
            binding.shareOutputFab.show()
          } else {
            binding.clearFab.hide()
            binding.shareOutputFab.hide()
          }
        }

        override fun onTabUnselected(tab: Tab) {}
        override fun onTabReselected(tab: Tab) {}
      }
    )

    binding.shareOutputFab.setOnClickListener {
      // 新增：记录分享按钮点击日志
      logToFile("shareOutputFab clicked")
      
      val fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)

      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        logToFile("shareOutputFab clicked but fragment is not ShareableOutputFragment: ${fragment.javaClass.simpleName}")
        return@setOnClickListener
      }

      val filename = fragment.getFilename()

      @Suppress("DEPRECATION")
      val progress = android.app.ProgressDialog.show(context, null,
        context.getString(string.please_wait))
      executeAsync(fragment::getContent) {
        progress.dismiss()
        shareText(it, filename)
      }
    }

    TooltipCompat.setTooltipText(binding.clearFab, context.getString(string.title_clear_output))
    binding.clearFab.setOnClickListener {
      // 新增：详细记录清除按钮点击日志
      logToFile("clearFab clicked, starting clear process")
      
      try {
        // 记录当前选中的标签位置
        val tabPosition = binding.tabs.selectedTabPosition
        logToFile("clearFab clicked - selected tab position: $tabPosition")
        
        // 获取当前Fragment
        val fragment: Fragment = pagerAdapter.getFragmentAtIndex(tabPosition)
        logToFile("clearFab clicked - fragment class: ${fragment.javaClass.name}")
        
        // 检查Fragment是否为null
        if (fragment == null) {
          log.error("Fragment is null at position $tabPosition")
          logToFile("clearFab error - fragment is null at position $tabPosition")
          return@setOnClickListener
        }
        
        // 检查Fragment类型
        if (fragment !is ShareableOutputFragment) {
          log.error("Unknown fragment: {}", fragment)
          logToFile("clearFab error - fragment is not ShareableOutputFragment: ${fragment.javaClass.name}")
          return@setOnClickListener
        }
        
        // 记录即将调用clearOutput()
        logToFile("clearFab - calling clearOutput() on ${fragment.javaClass.name}")
        
        // 调用清除方法
        logToFile("clearFab - before calling clearOutput(), fragment is null? ${fragment == null}")
        logToFile("clearFab - before calling clearOutput(), fragment is ShareableOutputFragment? ${fragment is ShareableOutputFragment}")
        fragment.clearOutput()
                
        // 记录清除成功
        logToFile("clearFab - clearOutput() completed successfully")

      } catch (t: Throwable) { // 捕获所有 Throwable（包括 Exception 和 Error）
        log.error("Error when clicking clearFab", t) // 打印完整堆栈到 Logcat
        logToFile("clearFab click Throwable: ${t.message}\n${getStackTraceString(t)}") // 写入本地日志
        flashError(context.getString(string.msg_failed_to_clear_output))

      }
    }

    binding.headerContainer.setOnClickListener {
      if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      this.windowInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
      insets
    }
  }

  init {
    // 新增：记录初始化日志
    logToFile("Creating EditorBottomSheet instance")
    
    if (context !is FragmentActivity) {
      val errorMsg = "EditorBottomSheet must be set up with a FragmentActivity"
      log.error(errorMsg)
      logToFile("Initialization error: $errorMsg")
      throw IllegalArgumentException(errorMsg)
    }

    val inflater = LayoutInflater.from(context)
    binding = LayoutEditorBottomSheetBinding.inflate(inflater)
    pagerAdapter = EditorBottomSheetTabAdapter(context)
    binding.pager.adapter = pagerAdapter

    removeAllViews()
    addView(binding.root)

    initialize(context)
  }

  /**
   * Set whether the input method is visible.
   */
  fun setImeVisible(isVisible: Boolean) {
    logToFile("setImeVisible - $isVisible")
    isImeVisible = isVisible
    behavior.isGestureInsetBottomIgnored = isVisible
  }

  fun setOffsetAnchor(view: View) {
    logToFile("setOffsetAnchor called with view: ${view.javaClass.simpleName}")
    
    val listener =
      object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
          view.viewTreeObserver.removeOnGlobalLayoutListener(this)
          anchorOffset = view.height + SizeUtils.dp2px(1f)
          logToFile("setOffsetAnchor - calculated anchorOffset: $anchorOffset")

          behavior.peekHeight = collapsedHeight.roundToInt()
          behavior.expandedOffset = anchorOffset
          behavior.isGestureInsetBottomIgnored = isImeVisible

          binding.root.updatePadding(bottom = anchorOffset + insetBottom)
          binding.headerContainer.apply {
            updatePaddingRelative(bottom = paddingBottom + insetBottom)
            updateLayoutParams<ViewGroup.LayoutParams> {
              height = (collapsedHeight + insetBottom).roundToInt()
            }
          }
        }
      }

    view.viewTreeObserver.addOnGlobalLayoutListener(listener)
  }

  fun onSlide(sheetOffset: Float) {
    val heightScale = if (sheetOffset >= COLLAPSE_HEADER_AT_OFFSET) {
      ((COLLAPSE_HEADER_AT_OFFSET - sheetOffset) + COLLAPSE_HEADER_AT_OFFSET) * 2f
    } else {
      1f
    }

    val paddingScale = if (!isImeVisible && sheetOffset <= COLLAPSE_HEADER_AT_OFFSET) {
      ((1f - sheetOffset) * 2f) - 1f
    } else {
      0f
    }
    
    val padding = insetBottom * paddingScale
    binding.headerContainer.apply {
      updateLayoutParams<ViewGroup.LayoutParams> {
        height = ((collapsedHeight + padding) * heightScale).roundToInt()
      }
      updatePaddingRelative(
        bottom = padding.roundToInt()
      )
    }
  }

  fun showChild(index: Int) {
    logToFile("showChild - index: $index")
    binding.headerContainer.displayedChild = index
  }

  fun setActionText(text: CharSequence) {
    logToFile("setActionText - $text")
    binding.bottomAction.actionText.text = text
  }

  fun setActionProgress(progress: Int) {
    logToFile("setActionProgress - $progress%")
    binding.bottomAction.progress.setProgressCompat(progress, true)
  }

    // 修复 appendApkLog 方法的空安全问题
    fun appendApkLog(line: LogLine) {
        val logContent = line.message?.take(50) ?: "null"
        logToFile("appendApkLog - $logContent...")
        pagerAdapter.logFragment?.appendLog(line)
    }
    
  fun appendBuildOut(str: String?) {
    logToFile("appendBuildOut - ${str?.take(50)}...") // 只记录前50个字符
    pagerAdapter.buildOutputFragment?.appendOutput(str)
  }

  fun clearBuildOutput() {
    logToFile("clearBuildOutput called")
    pagerAdapter.buildOutputFragment?.clearOutput()
  }

  fun handleDiagnosticsResultVisibility(errorVisible: Boolean) {
    logToFile("handleDiagnosticsResultVisibility - $errorVisible")
    runOnUiThread { pagerAdapter.diagnosticsFragment?.isEmpty = errorVisible }
  }

  fun handleSearchResultVisibility(errorVisible: Boolean) {
    logToFile("handleSearchResultVisibility - $errorVisible")
    runOnUiThread { pagerAdapter.searchResultFragment?.isEmpty = errorVisible }
  }

  fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) {
    logToFile("setDiagnosticsAdapter - ${adapter.itemCount} items")
    runOnUiThread { pagerAdapter.diagnosticsFragment?.setAdapter(adapter) }
  }

  fun setSearchResultAdapter(adapter: SearchListAdapter) {
    logToFile("setSearchResultAdapter - ${adapter.itemCount} items")
    runOnUiThread { pagerAdapter.searchResultFragment?.setAdapter(adapter) }
  }

  fun refreshSymbolInput(editor: CodeEditorView) {
    logToFile("refreshSymbolInput called for editor: ${editor.file?.name}")
    binding.symbolInput.refresh(editor.editor, forFile(editor.file))
  }

  fun onSoftInputChanged() {
    logToFile("onSoftInputChanged called")
    
    if (context !is Activity) {
      log.error("Bottom sheet is not attached to an activity!")
      logToFile("onSoftInputChanged error - not attached to an activity")
      return
    }

    binding.symbolInput.itemAnimator?.endAnimations()

    TransitionManager.beginDelayedTransition(
      binding.root,
      MaterialSharedAxis(MaterialSharedAxis.Y, false)
    )

    val activity = context as Activity
    if (KeyboardUtils.isSoftInputVisible(activity)) {
      logToFile("onSoftInputChanged - keyboard visible, showing symbol input")
      binding.headerContainer.displayedChild = CHILD_SYMBOL_INPUT
    } else {
      logToFile("onSoftInputChanged - keyboard hidden, showing header")
      binding.headerContainer.displayedChild = CHILD_HEADER
    }
  }

  fun setStatus(text: CharSequence, @GravityInt gravity: Int) {
    logToFile("setStatus - text: $text, gravity: $gravity")
    runOnUiThread {
      binding.buildStatus.let {
        it.statusText.gravity = gravity
        it.statusText.text = text
      }
    }
  }

  private fun shareFile(file: File) {
    logToFile("shareFile - ${file.name}")
    shareFile(context, file, "text/plain")
  }

    @Suppress("DEPRECATION")
    private fun shareText(text: String?, type: String) {
        logToFile("shareText - type: $type, text length: ${text?.length ?: 0}")
        
        if (text.isNullOrEmpty()) {
            log.error("Text to share is null or empty")
            logToFile("shareText error - text is null or empty")
            flashError(context.getString(string.msg_output_text_extraction_failed))
            return
        }
        
        val pd = android.app.ProgressDialog.show(
            context, 
            null, 
            context.getString(string.please_wait),
            true, 
            false
        )
        
        executeAsyncProvideError(
            Callable { writeTempFile(text, type) },
            // 修复接口实现错误，使用正确的方法名和参数类型
            object : CallbackWithError<File?> {
                override fun complete(result: File?, error: Throwable?) {
                    pd.dismiss()
                    
                    if (result == null || error != null) {
                        log.warn("Unable to share output", error)
                        logToFile("shareText error - ${error?.message ?: "Failed to create temp file"}")
                        flashError(context.getString(string.msg_failed_to_share_output))
                        return
                    }
                    
                    shareFile(result)
                }
            }
        )
    }
    
    // 修复 writeTempFile 方法的返回值和空安全处理
    private fun writeTempFile(text: String, type: String): File? {
        logToFile("writeTempFile - type: $type, text length: ${text.length}")
        
        return try {
            val tempFilePath: Path = context.filesDir.toPath().resolve("${type}_share.txt")
            val tempFile = tempFilePath.toFile()
            
            if (tempFile.exists()) {
                logToFile("writeTempFile - Deleting existing temp file: ${tempFile.name}")
                tempFile.delete()
            }
            
            Files.write(
                tempFilePath,
                text.toByteArray(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )
            
            logToFile("writeTempFile - Success: Temp file created at ${tempFile.absolutePath}")
            tempFile
        } catch (e: IOException) {
            log.error("Unable to write output to temp file", e)
            logToFile("writeTempFile error - ${e.message}\n${getStackTraceString(e)}")
            null
        }
    }

  // ---------------------- 日志工具方法（完整实现） ----------------------
  /**
   * 将日志写入本地文件（应用私有目录下的 editor_bottom_sheet_logs.txt）
   * @param message 日志内容
   */
  private fun logToFile(message: String) {
    // 避免主线程阻塞，异步写入日志（不影响UI响应）
    Thread {
      try {
        // 生成带时间戳的日志行（便于追溯时间顺序）
        val timestamp = logFormatter.format(Date())
        val logLine = "[$timestamp] [$logTag] $message\n"
        
        // 确保日志文件存在（首次调用时创建）
        if (!logFile.exists()) {
          logFile.createNewFile()
          logToFile("Log file initialized: ${logFile.absolutePath}") // 记录文件初始化
        }
        
        // 追加日志到文件（使用APPEND模式，避免覆盖历史日志）
        Files.write(
          logFile.toPath(),
          logLine.toByteArray(StandardCharsets.UTF_8),
          StandardOpenOption.APPEND,
          StandardOpenOption.CREATE // 防止文件被意外删除后无法写入
        )
        
        // 同时输出到Logcat（开发时实时查看）
        android.util.Log.d(logTag, message)
      } catch (e: Exception) {
        // 捕获日志写入异常，避免影响主逻辑
        android.util.Log.e(logTag, "Failed to write to log file: ${e.message}", e)
      }
    }.start()
  }

  /**
   * 将异常堆栈信息转换为字符串（便于日志记录）
   * @param throwable 异常实例
   * @return 包含完整堆栈的字符串
   */
  private fun getStackTraceString(throwable: Throwable): String {
    val stackTraceBuilder = StringBuilder()
    var currentThrowable: Throwable? = throwable
    
    // 遍历所有异常（包括cause链），避免遗漏根源异常
    while (currentThrowable != null) {
      stackTraceBuilder.append(currentThrowable.toString()).append("\n")
      
      // 追加当前异常的堆栈信息
      for (stackElement in currentThrowable.stackTrace) {
        stackTraceBuilder.append("    at ").append(stackElement).append("\n")
      }
      
      // 处理cause异常（若存在）
      currentThrowable = currentThrowable.cause
      if (currentThrowable != null) {
        stackTraceBuilder.append("Caused by: ")
      }
    }
    
    return stackTraceBuilder.toString()
  }

  // ---------------------- 底部弹窗状态控制方法 ----------------------
  /**
   * 展开底部弹窗
   */
  fun expand() {
    logToFile("expand() called - Current state: ${behavior.state}")
    if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
      behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
  }

  /**
   * 折叠底部弹窗（若支持折叠状态）
   */
  fun collapse() {
    logToFile("collapse() called - Current state: ${behavior.state}")
    if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
      behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
  }

  /**
   * 隐藏底部弹窗
   */
  fun hide() {
    logToFile("hide() called - Current state: ${behavior.state}")
    if (behavior.state != BottomSheetBehavior.STATE_HIDDEN) {
      behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }
  }

  /**
   * 检查底部弹窗是否处于展开状态
   * @return true：展开；false：未展开（折叠/隐藏）
   */
  fun isExpanded(): Boolean {
    return behavior.state == BottomSheetBehavior.STATE_EXPANDED
  }
}