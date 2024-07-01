package io.agora.online.widget.rtt

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.agora.agoraeducore.core.internal.framework.data.EduBaseUserInfo
import io.agora.agoraeducore.extensions.widgets.AgoraBaseWidget
import io.agora.online.R
import io.agora.online.component.common.IAgoraUIProvider
import io.agora.online.databinding.FcrOnlineToolBoxWidgetContentBinding
import io.agora.online.helper.IRttOptions
import io.agora.online.helper.RttOptionsManager
import io.agora.online.options.AgoraEduOptionsComponent
import io.agora.online.options.AgoraEduRttOptionsComponent
import java.text.MessageFormat

/**
 * 功能作用：Rtt工具箱widget
 * 初始注释时间： 2024/7/1 17:25
 * 创建人：王亮（Loren）
 * 思路：
 * 方法：
 * 注意：
 * 修改人：
 * 修改时间：
 * 备注：
 *
 * @author 王亮（Loren）
 */
class FcrRttToolBoxWidget : AgoraBaseWidget() {
    override val TAG = "FcrRttToolsBoxWidget"

    private var contentView: AgoraRttToolBoxWidgetContent? = null


    fun init(
        container: ViewGroup,
        agoraUIProvider: IAgoraUIProvider, agoraEduOptionsComponent: AgoraEduOptionsComponent, conversionStatusView: ViewGroup,
        subtitleView: AgoraEduRttOptionsComponent,
    ) {
        super.init(container)
        contentView = AgoraRttToolBoxWidgetContent(container, agoraUIProvider, agoraEduOptionsComponent, conversionStatusView, subtitleView)
    }

    override fun release() {
        contentView?.dispose()
        super.release()
    }

    override fun onWidgetRoomPropertiesUpdated(
        properties: MutableMap<String, Any>, cause: MutableMap<String, Any>?, keys: MutableList<String>,
        operator: EduBaseUserInfo?,
    ) {
        super.onWidgetRoomPropertiesUpdated(properties, cause, keys, operator)
    }

    /**
     * 重置工具准给他
     */
    fun resetEduRttToolBoxStatus() {
        contentView?.resetStatus()
    }


    internal inner class AgoraRttToolBoxWidgetContent(
        val container: ViewGroup, agoraUIProvider: IAgoraUIProvider,
        agoraEduOptionsComponent: AgoraEduOptionsComponent, conversionStatusView: ViewGroup, subtitleView: AgoraEduRttOptionsComponent,
    ) : IRttOptions {
        private val TAG = "AgoraRttToolBoxWidgetContent"

        /**
         * Rtt功能的管理
         */
        private val rttOptionsManager: RttOptionsManager by lazy {
            RttOptionsManager(this).also {
                it.initView(conversionStatusView, subtitleView, agoraUIProvider)
                it.setEduOptionsComponent(agoraEduOptionsComponent)
            }
        }

        /**
         * 内容视图
         */
        private var binding: FcrOnlineToolBoxWidgetContentBinding =
            FcrOnlineToolBoxWidgetContentBinding.inflate(LayoutInflater.from(container.context), container, true)

        init {
            binding.agoraRttDialogLayout.clipToOutline = true
            binding.agoraRttDialogSubtitles.setOnClickListener {
                if (this.rttOptionsManager.isOpenSubtitles()) {
                    binding.agoraRttDialogSubtitlesIcon.isActivated = false
                    this.rttOptionsManager.closeSubtitles()
                } else {
                    binding.agoraRttDialogSubtitlesIcon.isActivated = true
                    this.rttOptionsManager.openSubtitles()
                }
            }
            binding.agoraRttDialogConversion.setOnClickListener {
                if (this.rttOptionsManager.isOpenConversion()) {
                    binding.agoraRttDialogConversionIcon.isActivated = false
                    this.rttOptionsManager.closeConversion()
                } else {
                    binding.agoraRttDialogConversionIcon.isActivated = true
                    this.rttOptionsManager.openConversion()
                }
            }
            resetStatus()
        }

        /**
         * 重置显示状态
         */
        fun resetStatus() {
            val experienceReduceTime = rttOptionsManager.getExperienceReduceTime()
            binding.agoraRttDialogSubtitlesIcon.isActivated = rttOptionsManager.isOpenSubtitles()
            binding.agoraRttDialogConversionIcon.isActivated = rttOptionsManager.isOpenConversion()
            binding.fcrOnlineEduRttConversionDialogTimeLimitHint.text = if (experienceReduceTime > 0) {
                MessageFormat.format(container.context.getString(R.string.fcr_dialog_rtt_time_limit_time), experienceReduceTime / 60000)
            } else {
                container.context.getString(R.string.fcr_dialog_rtt_time_limit_end)
            }
        }

        /**
         * 设置是否可以使用RTT功能
         */
        fun setAllowUse(allowUse: Boolean, reduceTime: Int) {
            binding.fcrOnlineEduRttConversionDialogTimeLimitHint.visibility = if (reduceTime > 0) View.VISIBLE else View.GONE
            binding.agoraRttDialogSubtitles.isEnabled = allowUse
            binding.agoraRttDialogConversion.isEnabled = allowUse
            binding.agoraRttDialogSubtitles.alpha = if (allowUse) 1F else 0.8F
            binding.agoraRttDialogConversion.alpha = if (allowUse) 1F else 0.8F
        }

        fun dispose() {
            container.removeView(binding.root)
        }

        /**
         * 获取应用实例
         */
        override fun getApplicationContext(): Context {
            if (container.context is Activity) {
                return container.context.applicationContext
            }
            return container.context
        }

        /**
         * 当前页面实例
         */
        override fun getActivityContext(): Context {
            return container.context
        }

        /**
         * 判断并切换主线程
         */
        override fun runOnUiThread(runnable: Runnable) {
            container.post(runnable)
        }
    }
}