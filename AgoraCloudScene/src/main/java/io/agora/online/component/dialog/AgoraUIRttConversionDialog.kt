package io.agora.online.component.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.agora.online.R
import io.agora.online.databinding.FcrOnlineEduRttConversionDialogBinding
import io.agora.online.helper.RttRecordItem
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 功能作用：Rtt实时转写显示弹窗
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
class AgoraUIRttConversionDialog(context: Context) : Dialog(context, R.style.agora_full_screen_dialog) {

    private val binding: FcrOnlineEduRttConversionDialogBinding by lazy {
        FcrOnlineEduRttConversionDialogBinding.inflate(layoutInflater, null, false)
    }

    /**
     * 操作回调
     */
    var optionsCallback: ConversionOptionsInterface? = null

    /**
     * 适配器
     */
    private val mAdapter = RecordAdapter(context, arrayListOf())

    init {
        setContentView(binding.root)
        val window = this.window;
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.decorView?.setBackgroundResource(android.R.color.transparent)
        val layout = findViewById<ViewGroup>(R.id.agora_dialog_layout)
        layout.elevation = 10f

        initView()
    }

    private fun initView() {
        binding.fcrOnlineEduRttConversionDialogChangeStatus.setOnClickListener {
            if (it.isActivated) {
                closeConversion()
            } else {
                openConversion()
            }
        }
        binding.fcrOnlineEduRttConversionDialogOptionsSetting.setOnClickListener {
            optionsCallback?.openSetting()
        }
        binding.fcrOnlineEduRttConversionDialogOptionsClose.setOnClickListener { dismiss() }
        binding.fcrOnlineEduRttConversionDialogList.adapter = mAdapter
        binding.fcrOnlineEduRttConversionDialogList.layoutManager = LinearLayoutManager(context)
    }

    /**
     * 设置限时体验信息
     */
    fun setExperienceInfo(allowUseConfig: Boolean, rttExperienceReduceTime: Int) {
        binding.fcrOnlineEduRttConversionDialogChangeStatus.isEnabled = allowUseConfig || rttExperienceReduceTime > 0
        if (allowUseConfig) {
            binding.fcrOnlineEduRttConversionDialogTimeLimitHint.visibility = View.GONE
            binding.fcrOnlineEduRttConversionDialogTimeLimitReduce.visibility = View.GONE
        } else {
            binding.fcrOnlineEduRttConversionDialogTimeLimitHint.visibility = View.VISIBLE
            if (rttExperienceReduceTime <= 0) {
                binding.fcrOnlineEduRttConversionDialogTimeLimitHint.setText(R.string.fcr_dialog_rtt_time_limit_end)
                binding.fcrOnlineEduRttConversionDialogTimeLimitHint.setBackgroundResource(R.drawable.agora_options_rtt_time_limit_bg_disable)
                binding.fcrOnlineEduRttConversionDialogTimeLimitReduce.visibility = View.GONE
                closeConversion()
            } else {
                binding.fcrOnlineEduRttConversionDialogTimeLimitHint.setText(R.string.fcr_dialog_rtt_time_limit)
                binding.fcrOnlineEduRttConversionDialogTimeLimitHint.setBackgroundResource(R.drawable.agora_options_rtt_time_limit_bg)
                binding.fcrOnlineEduRttConversionDialogTimeLimitReduce.visibility = View.VISIBLE
                binding.fcrOnlineEduRttConversionDialogTimeLimitReduce.text = MessageFormat.format(
                    context.resources.getString(R.string.fcr_dialog_rtt_dialog_time_limit_reduce),
                    if (rttExperienceReduceTime / 60000 > 9) rttExperienceReduceTime / 60000 else "0${rttExperienceReduceTime / 60000}",
                    if (rttExperienceReduceTime % 60000 / 1000 > 9) rttExperienceReduceTime % 60000 / 1000 else "0${rttExperienceReduceTime % 60000 / 1000}",
                )
            }
        }
    }

    /**
     * 刷新记录信息
     */
    fun updateShowList(list: List<RttRecordItem>) {
        if (mAdapter.dataList.size != list.size) {
            mAdapter.dataList.clear()
            mAdapter.dataList.addAll(list)
            mAdapter.notifyItemRangeChanged(0, list.size)
            binding.fcrOnlineEduRttConversionDialogList.scrollToPosition(mAdapter.dataList.size - 1)
        } else {
            mAdapter.dataList[mAdapter.dataList.size - 1] = list[list.size - 1]
            mAdapter.notifyItemChanged(mAdapter.dataList.size - 1)
            binding.fcrOnlineEduRttConversionDialogList.scrollToPosition(mAdapter.dataList.size - 1)
        }
    }

    /**
     * 开始转写
     */
    private fun openConversion() {
        if (binding.fcrOnlineEduRttConversionDialogChangeStatus.isEnabled) {
            optionsCallback?.openConversion()
            binding.fcrOnlineEduRttConversionDialogChangeStatus.isActivated = true
            binding.fcrOnlineEduRttConversionDialogChangeStatus.setText(R.string.fcr_dialog_rtt_dialog_conversion_close)
            binding.fcrOnlineEduRttConversionDialogChangeStatus.setTextColor(ContextCompat.getColor(context, R.color.fcr_text_red))
        }
    }

    /**
     * 关闭转写
     */
    private fun closeConversion() {
        optionsCallback?.closeConversion()
        binding.fcrOnlineEduRttConversionDialogChangeStatus.isActivated = false
        binding.fcrOnlineEduRttConversionDialogChangeStatus.setText(R.string.fcr_dialog_rtt_dialog_conversion_open)
        binding.fcrOnlineEduRttConversionDialogChangeStatus.setTextColor(ContextCompat.getColor(context, R.color.fcr_white))
    }

    /**
     * 显示弹窗
     */
    fun show(list: List<RttRecordItem>) {
        mAdapter.dataList.clear()
        mAdapter.dataList.addAll(list)
        openConversion()
        super.show()
    }
}

/**
 * 转写操作接口类
 */
interface ConversionOptionsInterface {
    /**
     * 开启转写
     */
    fun openConversion()

    /**
     * 关闭转写
     */
    fun closeConversion()

    /**
     * 打开设置页面
     */
    fun openSetting()
}

/**
 * 数据记录适配器
 */
private class RecordAdapter(private val context: Context, val dataList: ArrayList<RttRecordItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (dataList[viewType].statusText.isNullOrEmpty()) {
            return object : RecyclerView.ViewHolder(
                LayoutInflater.from(context).inflate(R.layout.fcr_online_rtt_conversion_dialog_list_content, parent, false)) {}
        }
        return object :
            RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.fcr_online_rtt_conversion_dialog_list_options, parent, false)) {}
    }

    override fun getItemCount() = dataList.size

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val bean = dataList[position]
        if (bean.statusText.isNullOrEmpty()) {
            holder.itemView.let {
                Glide.with(context).load(bean.userHeader).skipMemoryCache(true).placeholder(R.drawable.agora_video_ic_audio_on)
                    .apply(RequestOptions.circleCropTransform()).into(it.findViewById(R.id.agora_fcr_rtt_text_dialog_user_header))
                it.findViewById<AppCompatTextView>(R.id.agora_fcr_rtt_text_dialog_user_name).text = bean.userName
                it.findViewById<AppCompatTextView>(R.id.agora_fcr_rtt_text_dialog_time).text =
                    (if (bean.time == null || bean.time == 0L) null else bean.time)?.let { time -> Date(time) }
                        ?.let { date -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(date) }
                it.findViewById<AppCompatTextView>(R.id.agora_fcr_rtt_text_dialog_text_origin).text = bean.sourceText

                it.findViewById<AppCompatTextView>(R.id.agora_fcr_rtt_text_dialog_text_result).apply {
                    text = if (bean.currentTargetLan.isNullOrEmpty()) "" else bean.targetText
                    visibility = if (bean.currentTargetLan.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
            }
        } else {
            holder.itemView.findViewById<AppCompatTextView>(R.id.fcr_online_rtt_conversion_dialog_list_options_text).text = bean.statusText
        }
    }
}