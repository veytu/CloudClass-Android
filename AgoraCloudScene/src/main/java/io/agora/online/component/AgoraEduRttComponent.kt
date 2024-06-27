package io.agora.online.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import io.agora.online.R
import io.agora.online.component.common.AbsAgoraEduComponent
import io.agora.online.component.common.IAgoraUIProvider
import io.agora.online.databinding.FcrOnlineEduRttComponentBinding
import io.agora.online.helper.RttOptionsManager
import io.agora.online.util.AppUtil
import java.text.MessageFormat

class AgoraEduRttComponent : AbsAgoraEduComponent {
    constructor(context: Context) : super(context)
    constructor(context: Context, attr: AttributeSet) : super(context, attr)
    constructor(context: Context, attr: AttributeSet, defStyleAttr: Int) : super(context, attr, defStyleAttr)


    private var binding: FcrOnlineEduRttComponentBinding = FcrOnlineEduRttComponentBinding.inflate(LayoutInflater.from(context), this, true)
    private lateinit var rttOptionsManager: RttOptionsManager

    //点击间隔时间
    private val clickInterval = 500L

    fun initView(rttOptionsManager: RttOptionsManager, agoraUIProvider: IAgoraUIProvider) {
        this.rttOptionsManager = rttOptionsManager
        super.initView(agoraUIProvider)
    }

    init {
        binding.agoraRttDialogLayout.clipToOutline = true
        setButtonClickListeners()
    }

    private fun setButtonClickListeners() {
        resetStatus(null)
        setFastClickAvoidanceListener(binding.agoraRttDialogSubtitles) {
            if (this.rttOptionsManager.isOpenSubtitles()) {
                binding.agoraRttDialogSubtitlesIcon.isActivated = false
                this.rttOptionsManager.closeSubtitles()
            } else {
                binding.agoraRttDialogSubtitlesIcon.isActivated = true
                this.rttOptionsManager.openSubtitles()
            }
        }
        setFastClickAvoidanceListener(binding.agoraRttDialogConversion) {
            if (this.rttOptionsManager.isOpenConversion()) {
                binding.agoraRttDialogConversionIcon.isActivated = false
                this.rttOptionsManager.closeConversion()
            } else {
                binding.agoraRttDialogConversionIcon.isActivated = true
                this.rttOptionsManager.openConversion()
            }
        }
    }


    private fun setFastClickAvoidanceListener(view: View, worker: ((Boolean) -> Unit)?) {
        view.setOnClickListener {
            if (!AppUtil.isFastClick(clickInterval)) {
                worker?.invoke(view.isActivated)
            }
        }
    }


    fun dismiss() {
//        this.parent?.let {
//            var contains = false
//            it.forEach check@{ child ->
//                if (child == this) {
//                    contains = true
//                    return@check
//                }
//            }
//
//            if (contains) {
//                it.removeView(this)
//            }
//        }
    }

    override fun release() {
        super.release()
    }

    /**
     * 重置显示状态
     */
    fun resetStatus(experienceReduceTime: Int?) {
        binding.agoraRttDialogSubtitlesIcon.isActivated = this::rttOptionsManager.isInitialized && rttOptionsManager.isOpenSubtitles()
        binding.agoraRttDialogConversionIcon.isActivated = this::rttOptionsManager.isInitialized && rttOptionsManager.isOpenConversion()
        if (experienceReduceTime != null) {
            binding.fcrOnlineEduRttConversionDialogTimeLimitHint.text = if (experienceReduceTime > 0) {
                MessageFormat.format(resources.getString(R.string.fcr_dialog_rtt_time_limit_time), experienceReduceTime / 60000)
            } else {
                resources.getString(R.string.fcr_dialog_rtt_time_limit_end)
            }
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
}
