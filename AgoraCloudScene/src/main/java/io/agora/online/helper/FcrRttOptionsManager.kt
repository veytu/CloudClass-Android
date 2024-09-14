package io.agora.online.helper

import android.content.Context
import android.os.Handler
import android.os.Message
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.agora.agoraeducore.core.AgoraEduCore
import io.agora.agoraeducore.core.context.AgoraEduContextUserInfo
import io.agora.agoraeducore.core.context.EduContextUserLeftReason
import io.agora.agoraeducore.core.context.StreamContext
import io.agora.agoraeducore.core.internal.base.http.AppRetrofitManager
import io.agora.agoraeducore.core.internal.education.impl.network.HttpBaseRes
import io.agora.agoraeducore.core.internal.education.impl.network.HttpCallback
import io.agora.agoraeducore.core.internal.framework.data.EduBaseUserInfo
import io.agora.agoraeducore.core.internal.framework.data.EduUserRole
import io.agora.agoraeducore.core.internal.framework.impl.handler.RoomHandler
import io.agora.agoraeducore.core.internal.framework.impl.handler.StreamHandler
import io.agora.agoraeducore.core.internal.framework.impl.handler.UserHandler
import io.agora.agoraeducore.core.internal.framework.utils.GsonUtil
import io.agora.agoraeducore.core.internal.log.LogX
import io.agora.online.R
import io.agora.online.component.common.IAgoraUIProvider
import io.agora.online.component.dialog.AgoraUIDialogBuilder
import io.agora.online.component.dialog.AgoraUIRttConversionDialogBuilder
import io.agora.online.component.dialog.AgoraUIRttSettingBuilder
import io.agora.online.component.dialog.AgoraUIRttSettingDialogListener
import io.agora.online.component.dialog.ConversionOptionsInterface
import io.agora.online.component.toast.AgoraUIToast
import io.agora.online.easeim.utils.TAG
import io.agora.online.easeim.view.ui.widget.RoomType
import io.agora.online.util.MultiLanguageUtil
import io.agora.online.util.SpUtil
import io.agora.rtc.speech2text.AgoraSpeech2TextProtobuffer
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.Locale
import java.util.UUID

/**
 * 功能作用：Rtt相关操作管理
 * 创建人：王亮（Loren）
 * 思路：
 * 方法：
 * 注意：
 * 修改人：
 * 修改时间：
 * 备注：
 *
 * 需求：
 * 如有人正在说话，字母区域提示：listening（正在聆听） ...
 * 如没有人说话，提示：no one is currently speaking（当前没有人在说话）
 * 房间内有音频输出时，字幕区域显示对应转写文字，无音频输出时，字幕区域在音频停止3S后自动消失
 *
 * 场景：
 * 音频流结束&翻译未结束
 * 音频流未结束&翻译未结束
 * 音频流未结束&上一次翻译结束
 * 音频流结束&当前翻译结束
 *
 *
 * 实现逻辑：
 *
 *
 *
 * @author 王亮（Loren）
 */
class RttOptionsManager(internal val rttOptions: IRttOptions) {
    private val TAG = "RttOptionsManager:"

    /**
     * 教室相关信息
     */
    internal var eduCore: AgoraEduCore? = null

    private val listenerList = arrayListOf<FcrRttOptionsStatusListener>()

    /**
     * 转写管理
     */
    private val conversionManager by lazy {
        RttConversionManager(this, this.getManagerListener())
    }

    /**
     * 字幕管理
     */
    private val subtitlesManager by lazy { RttSubtitlesManager(this, getManagerListener()) }

    /**
     * 设置管理
     */
    private val settingsManager by lazy { RttSettingManager(this) }

    /**
     * 使用管理
     */
    private val useManager by lazy { RttUseManager(this, false, getManagerListener()) }

    /**
     * 流回调监听
     */
    private val steamHandler = object : StreamHandler() {
        override fun onStreamMessage(channelId: String, streamId: Int, data: ByteArray?) {
            super.onStreamMessage(channelId, streamId, data)
            //不允许使用或者已到体验时间就不在回调，在分组中也不对消息数据进行处理
            if (!isAllowUseRtt() || (!conversionManager.isOpenConversion() && !subtitlesManager.isOpenSubtitles()) || checkUserInSubRoom()) {
                return
            }
            val parseFrom = AgoraSpeech2TextProtobuffer.Text.parseFrom(data)
            val recordItem = useManager.disposeData(parseFrom, settingsManager.currentSettingInfo, eduCore?.eduContextPool()?.streamContext())
            subtitlesManager.setShowCurrentData(recordItem, settingsManager.currentSettingInfo)
            conversionManager.updateShowList(useManager.getShowRecordList())
            LogX.i(TAG, "onStreamMessage channelId=$channelId, streamId=$streamId, data=${GsonUtil.toJson(parseFrom)}")

        }

        override fun onStreamMessageError(
            channelId: String,
            streamId: Int,
            error: Int,
            missed: Int,
            cached: Int,
        ) {
            super.onStreamMessageError(channelId, streamId, error, missed, cached)
            LogX.i(TAG, "onStreamMessageError channelId=$channelId, streamId=$streamId, error=$error, missed=$missed, cached=$cached")
        }
    }

    /**
     * 房间回调监听
     */
    private val roomHandler = object : RoomHandler() {
        override fun onRoomPropertiesUpdated(properties: Map<String, Any>, cause: Map<String, Any>?, operator: AgoraEduContextUserInfo?) {
            super.onRoomPropertiesUpdated(properties, cause, operator)
            LogX.i(TAG, "onRoomPropertiesUpdated properties=${GsonUtil.toJson(properties)}, cause=${
                cause?.let { GsonUtil.toJson(it) }
            }, operator=${operator?.let { GsonUtil.toJson(it) }}")
        }
    }

    /**
     * 用户监听
     */
    private val userHandler = object : UserHandler() {
        override fun onRemoteUserLeft(user: AgoraEduContextUserInfo, operator: AgoraEduContextUserInfo?, reason: EduContextUserLeftReason) {
            super.onRemoteUserLeft(user, operator, reason)
            useManager.formatAllUserInfo(eduCore?.eduContextPool()?.streamContext())
        }

        override fun onRemoteUserJoined(user: AgoraEduContextUserInfo) {
            super.onRemoteUserJoined(user)
            useManager.formatAllUserInfo(eduCore?.eduContextPool()?.streamContext())
        }
    }

    init {
        AgoraUIToast.init(rttOptions.getApplicationContext())
    }

    /**
     * 获取管理器监听
     */
    private fun getManagerListener(): FcrRttOptionsStatusListener {
        return object : FcrRttOptionsStatusListener() {
            /**
             * rtt功能状态变更
             * @param open 开启-true，关闭-false
             */
            override fun rttStateChange(open: Boolean) {
                listenerList.forEach { it.rttStateChange(open) }
            }

            /**
             * 音频状态-无人讲话
             */
            override fun audioStateNoSpeaking() {
                listenerList.forEach { it.audioStateNoSpeaking() }
            }

            /**
             * 音频状态-有人讲话
             */
            override fun audioStateSpeaking() {
                listenerList.forEach { it.audioStateSpeaking() }
            }

            /**
             * 音频状态-超过一定时间无人讲话
             */
            override fun audioStateNoSpeakingMoreTime() {
                listenerList.forEach { it.audioStateNoSpeakingMoreTime() }
            }

            /**
             * 音频状态-开启中
             */
            override fun audioStateOpening() {
                listenerList.forEach { it.audioStateOpening() }
            }

            /**
             * 音频状态-无法使用
             */
            override fun audioStateNotAllowUse() {
                listenerList.forEach { it.audioStateNotAllowUse() }
            }

            /**
             * 音频状态-显示设置提示
             */
            override fun audioStateShowSettingHint() {
                listenerList.forEach { it.audioStateShowSettingHint() }
            }

            /**
             * 体验信息变更
             * @param configAllowUseRtt 配置是否可以使用rtt功能
             * @param experienceReduceTime 剩余体验时间
             * @param experienceDefaultTime 默认体验时间
             */
            override fun experienceInfoChange(configAllowUseRtt: Boolean, experienceDefaultTime: Int, experienceReduceTime: Int) {
                conversionManager.setExperienceInfo(configAllowUseRtt, experienceReduceTime)
                listenerList.forEach { it.experienceInfoChange(configAllowUseRtt, experienceDefaultTime, experienceReduceTime) }
            }

            /**
             * 字幕状态变更
             * @param toOpen 开启-true，关闭-false
             */
            override fun subtitlesStateChange(toOpen: Boolean) {
                listenerList.forEach { it.subtitlesStateChange(toOpen) }
            }

            /**
             * 字幕视图重置
             */
            override fun subtitlesViewReset(openSuccess: Boolean) {
                listenerList.forEach { it.subtitlesViewReset(openSuccess) }
            }

            /**
             * 转写状态变更
             * @param toOpen 开启-true，关闭-false
             */
            override fun conversionStateChange(toOpen: Boolean) {
                if (conversionManager.isOpenConversion() == toOpen) {
                    return
                }
                listenerList.forEach { it.conversionStateChange(toOpen) }
            }

            /**
             * 转写视图重置
             */
            override fun conversionViewReset() {
                listenerList.forEach { it.conversionViewReset() }
            }

            /**
             * 声源语言修改
             */
            override fun sourceLanguageChange(language: RttLanguageEnum) {
                listenerList.forEach { it.sourceLanguageChange(language) }
            }

            /**
             * 目标语言修改-网络请求结果
             */
            override fun targetLanguageChange(languages: List<RttLanguageEnum>) {
                listenerList.forEach { it.targetLanguageChange(languages) }
            }

            /**
             * 双语状态变更
             * @param open 开启-true，关闭-false
             */
            override fun showDoubleLanguage(open: Boolean) {
                listenerList.forEach { it.showDoubleLanguage(open) }
            }

            /**
             * 双语状态变更-网络请求结果
             * @param open 开启-true，关闭-false
             */
            override fun showDoubleLanguageNetResult(open: Boolean) {
                listenerList.forEach { it.showDoubleLanguageNetResult(open) }
            }

            /**
             * 消息改变
             * @param currentData 当前要显示的数据
             */
            override fun onMessageChange(currentData: RttRecordItem?) {
                listenerList.forEach { it.onMessageChange(currentData) }
            }
        }
    }

    /**
     * 检测用户是否在分组中
     */
    fun checkUserInSubRoom():Boolean{
       return eduCore?.eduContextPool()?.roomContext()?.getRoomInfo()?.roomType?.value == RoomType.GROUPING_CLASS.value
    }

    /**
     * 新增监听
     */
    fun addListener(listener: FcrRttOptionsStatusListener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener)
        }
    }

    /**
     * 移除监听
     */
    fun removeListener(listener: FcrRttOptionsStatusListener) {
        if (listenerList.contains(listener)) {
            listenerList.remove(listener)
        }
    }

    /**
     * 初始化视图
     * @param agoraUIProvider UI配置信息
     */
    fun initView(agoraUIProvider: IAgoraUIProvider) {
        this.eduCore = agoraUIProvider.getAgoraEduCore()
        //重置视图
        this.setRttFunctionStatusConfig()
        //添加监听
        this.eduCore?.eduContextPool()?.streamContext()?.addHandler(steamHandler)
        this.eduCore?.eduContextPool()?.roomContext()?.addHandler(roomHandler)
        this.eduCore?.eduContextPool()?.userContext()?.addHandler(userHandler)
    }

    /**
     * 是否开启了字幕
     */
    fun isOpenSubtitles(): Boolean {
        return subtitlesManager.isOpenSubtitles()
    }

    /**
     * 是否显示双语
     */
    fun isShowDoubleLan(): Boolean {
        return settingsManager.currentSettingInfo.isShowDoubleLan()
    }

    /**
     * 是否开启了转写
     */
    fun isOpenConversion(): Boolean {
        return conversionManager.isOpenConversion()
    }

    /**
     * 是否允许使用Rtt功能
     */
    fun isAllowUseRtt(): Boolean {
        return useManager.isAllowUse()
    }

    /**
     * 设置RTT功能状态配置
     * @param allowUse 是否允许使用rtt功能，不可使用的话则提供体验时间
     */
    private fun setRttFunctionStatusConfig(allowUse: Boolean = false) {
        useManager.configAllowUse = allowUse
        if (!allowUse) {
            this.resetShow()
        }
    }

    /**
     * 释放相关
     */
    fun release() {
        listenerList.forEach { it.conversionViewReset();it.subtitlesViewReset(false) }
        this.eduCore?.eduContextPool()?.streamContext()?.removeHandler(steamHandler)
        this.eduCore?.eduContextPool()?.roomContext()?.removeHandler(roomHandler)
        this.eduCore?.eduContextPool()?.userContext()?.removeHandler(userHandler)
        listenerList.clear()
    }

    /**
     * 开启字幕
     */
    fun openSubtitles() {
        subtitlesManager.openSubtitles(useManager.configAllowUse, useManager.rttExperienceDefaultTime, useManager.rttExperienceReduceTime)
    }

    /**
     * 关闭字幕
     */
    fun closeSubtitles() {
        subtitlesManager.closeSubtitles()
    }

    /**
     * 开启转写
     */
    fun openConversion() {
        this.localIsChangeTranscribeState = true
        conversionManager.openConversion(useManager.getShowRecordList())
    }

    /**
     * 关闭转写
     */
    fun closeConversion() {
        conversionManager.closeConversion()
    }

    /**
     * 开启设置
     */
    fun openSetting() {
        this.settingsManager.openSetting()
    }

    /**
     * 关闭设置
     */
    fun closeSetting() {
        this.settingsManager.closeSetting()
    }

    /**
     * 开启体验
     */
    fun startExperience() {
        this.useManager.startExperience()
    }

    /**
     * 关闭体验
     */
    fun stopExperience() {
        this.useManager.stopExperience()
    }

    /**
     * 设置转换源语言
     */
    fun setSourceLanguage(lan: RttLanguageEnum) {
        settingsManager.currentSettingInfo.setSourceLan(lan)
        this.localIsChangeSourceLan = true
        sendRequest(isOpenConversion(), isOpenSubtitles(), object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
            override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                useManager.setLastFinal()
                this@RttOptionsManager.getManagerListener().sourceLanguageChange(lan)
            }
        })
    }

    /**
     * 设置转换目标语言
     */
    fun setTargetLanguage(lan: RttLanguageEnum) {
        settingsManager.currentSettingInfo.setTargetLan(lan)
        this.localIsChangeTarget = true
        sendRequest(isOpenConversion(), isOpenSubtitles(), object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
            override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                useManager.setLastFinal()
                this@RttOptionsManager.getManagerListener().targetLanguageChange(settingsManager.currentSettingInfo.getTargetLanList())
            }
        })
    }

    /**
     * 硒鼓双语显示
     */
    fun changeDoubleShow(showDouble: Boolean) {
        settingsManager.currentSettingInfo.setShowDoubleLan(showDouble)
    }

    /**
     * 获取体验剩余时间
     */
    fun getExperienceReduceTime(): Int {
        return useManager.rttExperienceReduceTime
    }

    /**
     * 获取体验默认时间
     */
    fun getExperienceDefaultTime(): Int {
        return useManager.rttExperienceDefaultTime
    }

    /**
     * 设置语言列表
     */
    fun setSourceListLanguages(list: ArrayList<RttLanguageEnum>) {
        this.settingsManager.currentSettingInfo.sourceListLan.clear()
        this.settingsManager.currentSettingInfo.sourceListLan.addAll(list)
        this.settingsManager.currentSettingInfo.sourceListLan.distinct()
    }

    /**
     * 新增语言列表
     */
    fun setTargetListLanguages(list: ArrayList<RttLanguageEnum>) {
        this.settingsManager.currentSettingInfo.targetListLan.clear()
        this.settingsManager.currentSettingInfo.targetListLan.addAll(list)
        this.settingsManager.currentSettingInfo.sourceListLan.distinct()
    }

    //是否正在发起请求
    private var isSendRequesting = false

    /**
     * 发起请求
     * @param openRtt 是否打开rtt功能
     * @param openRttSubtitles 是否开启字幕
     */
    internal fun sendRequest(
        openRttConversion: Boolean, openRttSubtitles: Boolean,
        callback: HttpCallback<HttpBaseRes<RttChangeOptionsRes>>? = null,
    ) {
        if (isSendRequesting) {
            return
        }
        isSendRequesting = true
        val body = FcrRttChangeOptionsData.formatUseData(openRttConversion, openRttSubtitles, settingsManager.currentSettingInfo.getSourceLan().value,
            if (openRttConversion || openRttSubtitles)  settingsManager.currentSettingInfo.getTargetLanList().filter { "" != it.value }.map { it.value }.distinct().toTypedArray() else arrayOf())
        val call = AppRetrofitManager.instance().getService(IRttOptionsService::class.java)
            .buildTokens(eduCore?.config?.appId, eduCore?.config?.roomUuid, if (openRttConversion || openRttSubtitles) 1 else 0, body)
        AppRetrofitManager.exc(call, object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
            override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                useManager.setExperienceReduceTime(useManager.rttExperienceDefaultTime - ((result?.data?.duration ?: 0) * 1000))
                callback?.onSuccess(result)
            }

            override fun onError(httpCode: Int, code: Int, message: String?) {
                if (httpCode == 409) {
                    //已经开始了转写翻译任务
                    callback?.onSuccess(null)
                    return
                }
                callback?.onError(httpCode, code, message)
            }

            override fun onComplete() {
                super.onComplete()
                isSendRequesting = false
            }
        })
    }

    /**
     * 重置显示
     */
    private fun resetShow() {
        listenerList.forEach { it.conversionViewReset();it.subtitlesViewReset(false) }
    }

    /**
     * 本地是否修改了声源语言,因为未返回实际的修改字段，所以需要做首次获取的更新处理，因为首次开启关闭会触发这个更新，但是实际上声源不一定会有修改
     */
    private var localIsChangeSourceLan = false

    /**
     * 本地是否修改了转写状态，原因同上
     */
    private var localIsChangeTranscribeState = false

    /**
     * 本地是否修改了翻译状态，原因同上
     */
    private var localIsChangeTarget = false

    /**
     * 房间widget属性更新
     */
    fun onWidgetRoomPropertiesUpdated(properties: MutableMap<String, Any>, operator: EduBaseUserInfo?) {
        if ("server" != operator?.userUuid) {
            GsonUtil.jsonToObject<FcrRttChangeOptionsData>(GsonUtil.toJson(properties))?.let {
                operator?.let { userInfo ->
                    LogX.i(TAG, "onWidgetRoomPropertiesUpdated result=${GsonUtil.toJson(properties)}__user=${GsonUtil.toJson(operator)}")
                    //剩余体验时间
                    useManager.setExperienceReduceTime(useManager.rttExperienceDefaultTime - (it.duration * 1000))
                    //用户信息
                    val localUserInfo = eduCore?.eduContextPool()?.userContext()?.getLocalUserInfo()
                    //判断是否改变了转写状态
                    if (checkUpdateConversion(it) || localIsChangeTranscribeState && operator.userUuid !== localUserInfo?.userUuid) {
                        this.localIsChangeTranscribeState = false
                        conversionManager.addStateChangeTextMessage(it.transcribe, userInfo, localUserInfo, useManager)
                        conversionManager.initOpenConversion(1 == it.transcribe)
                    }
                    //判断是否开启了翻译(仅当自己生效)
                    if (localIsChangeTarget && operator.userUuid === localUserInfo?.userUuid && !it.languages?.target.isNullOrEmpty() && this.localIsChangeTarget) {
                        this.localIsChangeTarget = false
                        settingsManager.currentSettingInfo.setTargetLanList(
                            it.languages?.target?.map { mapItem -> RttLanguageEnum.values().find { it.value == mapItem }!! }?.toTypedArray()
                                ?: arrayOf())
                        conversionManager.addTargetLanguageChangeMessage(true, userInfo, useManager)
                    }
                    //判断是否修改了声源语言
                    if (!it.languages?.source.isNullOrEmpty() && it.languages?.source != settingsManager.currentSettingInfo.getSourceLan().value || localIsChangeSourceLan && operator.userUuid === localUserInfo?.userUuid) {
                        this.localIsChangeSourceLan = false
                        val sourceEnum = RttLanguageEnum.values().find { item -> item.value == it.languages?.source }
                        if (sourceEnum != null) {
                            settingsManager.currentSettingInfo.setSourceLan(sourceEnum)
                            conversionManager.addSourceLanguageChangeMessage(userInfo, localUserInfo, useManager, settingsManager, sourceEnum)
                        }
                    }

                    //是否开启字幕
                    if (0 == it.subtitle && this.isOpenSubtitles()) {
                        //重新发起字幕开启
                        sendRequest(this.isOpenConversion(), openRttSubtitles = this.isOpenSubtitles(),
                            callback = object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
                                override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                                }

                                override fun onError(httpCode: Int, code: Int, message: String?) {
                                }
                            })
                    }
                }
            }
        }
    }

    /**
     * 初始化widget属性
     */
    fun onWidgetRoomPropertiesInit(properties: MutableMap<String, Any>?) {
        if (properties != null) {
            GsonUtil.jsonToObject<FcrRttChangeOptionsData>(GsonUtil.toJson(properties))?.let {
                LogX.i(TAG, "onWidgetRoomPropertiesInit result=${GsonUtil.toJson(properties)}}")
                //剩余体验时间
                useManager.setExperienceReduceTime(useManager.rttExperienceDefaultTime - (it.duration * 1000))
                //声源语言
                val sourceEnum = RttLanguageEnum.values().find { item -> item.value == it.languages?.source }
                if (sourceEnum != null) {
                    settingsManager.currentSettingInfo.setSourceLan(sourceEnum)
                }
            }
        }
    }

    /**
     * 检测是否更新了转写状态
     */
    private fun checkUpdateConversion(it: FcrRttChangeOptionsData): Boolean {
        if (1 == it.transcribe) {
            if (!conversionManager.isOpenConversion()) {
                return true
            }
        } else if (0 == it.transcribe) {
            if (conversionManager.isOpenConversion()) {
                return true
            }
        }
        return false
    }

    /**
     * 格式化名称角色显示
     * @param optionsUser 发起设置修改的用户信息
     * @param localUser 当前本地的用户信息
     */
    fun formatRoleName(optionsUser: EduBaseUserInfo, localUser: AgoraEduContextUserInfo?): String {
        return "${
            if (EduUserRole.STUDENT == optionsUser.role) {
                rttOptions.getApplicationContext().getString(R.string.fcr_role_student)
            } else if (EduUserRole.TEACHER == optionsUser.role) {
                rttOptions.getApplicationContext().getString(R.string.fcr_role_teacher)
            } else {
                ""
            }
        }(${
            if (optionsUser.userUuid == localUser?.userUuid) {
                rttOptions.getApplicationContext().getString(R.string.fcr_dialog_rtt_text_role_me)
            } else {
                optionsUser.userName
            }
        }) "
    }

}

/**
 * rtt操作接口
 */
interface IRttOptions {
    /**
     * 获取应用实例
     */
    fun getApplicationContext(): Context

    /**
     * 当前页面实例
     */
    fun getActivityContext(): Context

    /**
     * 判断并切换主线程
     */
    fun runOnUiThread(runnable: Runnable)
}

/**
 * 接口中请求的语言配置信息
 */
internal class RttChangOptionsLanguage(val source: String, targetList: Array<String>) {
    val target: Array<String> = targetList.filter { it.isNotEmpty() }.toTypedArray()
}

/**
 * 接口请求的语言配置信息响应
 */
internal data class RttChangeOptionsRes(
    /**
     * 已用时间
     */
    val duration: Int? = null,
    /**
     * 语言配置
     */
    val languages: RttChangOptionsLanguage? = null,
    /**
     * 开始时间
     */
    val startTime: Long? = null,
    /**
     * 1-开启rtt，0-关闭rtt
     */
    val state: Int? = null,
    /**
     * 流Id
     */
    val streamUuid: String? = null,
    /**
     * 1-开启了字幕，0-关闭了字幕
     */
    val subtitle: Int? = null,
    /**
     * 任务id
     */
    val taskId: String? = null,
    /**
     * token信息
     */
    val token: String? = null,
    /**
     * 1-开启了转写，0-关闭了转写
     */
    val transcribe: Int? = null,
)

/**
 * RTT转写记录
 */
class RttRecordItemTran {
    /**
     * 目标语言
     */
    var language: RttLanguageEnum? = null

    /**
     * 文案
     */
    var text: String? = null
}

/**
 * RTT记录
 */
class RttRecordItem {
    /**
     * 转写和字幕翻译之外的状态文本
     */
    var statusText: String? = null

    /**
     * 唯一id
     */
    var uuid: String? = null

    /**
     * 是否显示双语
     */
    var showDoubleLan: Boolean = false

    /**
     * 语言信息
     */
    var sourceLan: RttLanguageEnum? = null

    /**
     * 当前翻译的目标语言
     */
    var currentTargetLan: RttLanguageEnum? = null

    /**
     * 置信度
     */
    var sourceConfidence: Double? = null

    /**
     * 转写内容对应的Rtc uid
     */
    var uid: Long? = null

    /**
     * 说话的用户昵称
     */
    var userName: String? = null

    /**
     * 说话的用户头像
     */
    var userHeader: String? = null

    /**
     * 源文案
     */
    var sourceText: String? = null

    /**
     * 目标文案
     */
    var targetText: String? = null

    /**
     * 目标信息
     */
    var targetInfo: ArrayList<RttRecordItemTran>? = null

    /**
     * 时间
     */
    var time: Long? = null

    /**
     * 是否是最终结果
     */
    var isFinal: Boolean = false


    /**
     * 获取显示的源文本信息
     * @returns
     */
    fun getShowText(): Array<String?> {
        //是否设置了翻译语言
        val enableTargetLan = "" !== currentTargetLan?.value
        //语言显示
        val leve2Text =
            if (showDoubleLan && enableTargetLan && currentTargetLan != null && sourceLan != null && sourceLan!!.value !== currentTargetLan?.value) targetText else null
        val leve1Text = if (!showDoubleLan && enableTargetLan && !targetText.isNullOrEmpty()) targetText else sourceText
        return arrayOf(leve1Text, leve2Text)
    }

}

/**
 * 当前语言配置信息
 * @param sourceListLan 可用的源语言列表
 * @param targetListLan 可用的目标语言列表
 * @param sourceLan 转换的源语言
 * @param targetLan 转换的目标语言
 * @param showDoubleLan 是否显示双语
 */
class RttSettingInfo(
    var rttOptionsManager: RttOptionsManager,
    val sourceListLan: ArrayList<RttLanguageEnum> = arrayListOf(RttLanguageEnum.ZH_CN, RttLanguageEnum.EN_US, RttLanguageEnum.JA_JP),
    val targetListLan: ArrayList<RttLanguageEnum> = arrayListOf(RttLanguageEnum.NONE, RttLanguageEnum.ZH_CN, RttLanguageEnum.EN_US,
        RttLanguageEnum.JA_JP),
    private var sourceLan: RttLanguageEnum = RttLanguageEnum.ZH_CN,
    private var targetLanList: ArrayList<RttLanguageEnum> = arrayListOf(RttLanguageEnum.NONE),
    private var targetLan:RttLanguageEnum =RttLanguageEnum.NONE,
    private var showDoubleLan: Boolean = SpUtil.getBoolean(rttOptionsManager.rttOptions.getApplicationContext(),
        "${rttOptionsManager.eduCore?.config?.roomUuid ?: ""}_showDoubleLan", false),
) {
    init {
        val locale: Locale = MultiLanguageUtil.getAppLocale(rttOptionsManager.rttOptions.getActivityContext())
        // zh_CN ||  zh_TW
        val sysLanguage = locale.language // zh
        val sysCountry = locale.country // CN
        //默认源语言设置
        sourceLan =
            if (sysLanguage.equals(Locale.SIMPLIFIED_CHINESE.language, ignoreCase = true) && sysCountry.equals(Locale.SIMPLIFIED_CHINESE.country,
                    ignoreCase = true)) {
                RttLanguageEnum.ZH_CN
            } else if (sysLanguage.equals(Locale.US.language, ignoreCase = true) && sysCountry.equals(Locale.US.country, ignoreCase = true)) {
                RttLanguageEnum.EN_US
            } else if (Locale.getDefault().language.equals(Locale.SIMPLIFIED_CHINESE.language,
                    ignoreCase = true) && Locale.getDefault().country.equals(Locale.SIMPLIFIED_CHINESE.country, ignoreCase = true)) {
                RttLanguageEnum.ZH_CN
            } else if (Locale.getDefault().language.equals(Locale.US.language, ignoreCase = true) && Locale.getDefault().country.equals(
                    Locale.US.country, ignoreCase = true)) {
                RttLanguageEnum.EN_US
            } else {
                RttLanguageEnum.EN_US
            }
    }

    fun getSourceLan() = sourceLan
    fun getTargetLanList() = targetLanList
    fun getTargetLan() = targetLan
    fun isShowDoubleLan() = showDoubleLan
    fun setSourceLan(sourceLan: RttLanguageEnum) {
        this.sourceLan = sourceLan
    }

    fun setTargetLan(targetLan: RttLanguageEnum) {
        this.targetLan = targetLan
        this.targetLanList.add(targetLan)
    }

    fun setTargetLanList(targetLan: Array<RttLanguageEnum>) {
        this.targetLanList = targetLan.toCollection(arrayListOf())
    }

    fun setShowDoubleLan(show: Boolean) {
        this.showDoubleLan = show
        SpUtil.saveBoolean(rttOptionsManager.rttOptions.getApplicationContext(), "${rttOptionsManager.eduCore?.config?.roomUuid ?: ""}_showDoubleLan",
            show)
    }
}

/**
 * RTT可用语言枚举
 */
enum class RttLanguageEnum(@StringRes val textRes: Int, val value: String) {
    /**
     *  不翻译
     */
    NONE(R.string.fcr_dialog_rtt_language_none, ""),

    /**
     *  Chinese (Cantonese, Traditional)
     */
    ZH_HK(R.string.fcr_dialog_rtt_language_zh_hk, "zh-HK"),

    /**
     *  Chinese (Mandarin, Simplified)
     */
    ZH_CN(R.string.fcr_dialog_rtt_language_zh_cn, "zh-CN"),

    /**
     *  Chinese (Taiwanese Putonghua)
     */
    ZH_TW(R.string.fcr_dialog_rtt_language_zh_tw, "zh-TW"),

    /**
     * English (India)
     */
    EN_IN(R.string.fcr_dialog_rtt_language_en_in, "en-IN"),

    /**
     * English (US)
     */
    EN_US(R.string.fcr_dialog_rtt_language_en_us, "en-US"),

    /**
     * French (French)
     */
    FR_FR(R.string.fcr_dialog_rtt_language_fr_fr, "fr-FR"),

    /**
     * German (Germany)
     */
    DE_DE(R.string.fcr_dialog_rtt_language_de_de, "de-DE"),

    /**
     * Hai (Thailand)
     */
    TH_TH(R.string.fcr_dialog_rtt_language_th_th, "th-TH"),

    /**
     * Hindi (India)
     */
    HI_IN(R.string.fcr_dialog_rtt_language_hi_in, "hi-IN"),

    /**
     * Indonesian (Indonesia)
     */
    ID_ID(R.string.fcr_dialog_rtt_language_id_id, "id-ID"),

    /**
     * Italian (Italy)
     */
    IT_IT(R.string.fcr_dialog_rtt_language_it_it, "it-IT"),

    /**
     * Japanese (Japan)
     */
    JA_JP(R.string.fcr_dialog_rtt_language_ja_jp, "ja-JP"),

    /**
     * Korean (South Korea)
     */
    KO_KR(R.string.fcr_dialog_rtt_language_ko_kr, "ko-KR"),

    /**
     * Malay (Malaysia)
     */
    MS_MY(R.string.fcr_dialog_rtt_language_ms_my, "ms-MY*"),

    /**
     * Persian (Iran)
     */
    FA_IR(R.string.fcr_dialog_rtt_language_fa_ir, "fa-IR*"),

    /**
     * Portuguese (Portugal)
     */
    PT_PT(R.string.fcr_dialog_rtt_language_pt_pt, "pt-PT"),

    /**
     * Russian (Russia)
     */
    RU_RU(R.string.fcr_dialog_rtt_language_ru_ru, "ru-RU"),

    /**
     * Spanish (Spain)
     */
    ES_ES(R.string.fcr_dialog_rtt_language_es_es, "es-ES"),

    /**
     * Turkish (Turkey)
     */
    TR_TR(R.string.fcr_dialog_rtt_language_tr_tr, "tr-TR"),

    /**
     * Vietnamese (Vietnam)
     */
    VI_VN(R.string.fcr_dialog_rtt_language_vi_vn, "vi-VN"),
}

private interface IRttOptionsService {
    /**
     * 修改RTT配置信息
     */
    @Headers("Content-Type: application/json")
    @PUT("edu/apps/{appId}/v2/rooms/{roomId}/widgets/rtt/states/{state}")
    fun buildTokens(
        @Path("appId") appId: String?, @Path("roomId") roomId: String?, @Path("state") state: Int?,
        @Body body: FcrRttChangeOptionsData,
    ): Call<HttpBaseRes<RttChangeOptionsRes>>
}

/**
 * 修改RTT配置操作请求实体
 */
private data class FcrRttChangeOptionsData(
    /**
     * 语言配置
     */
    var languages: RttChangOptionsLanguage? = null,

    /**
     * 是否开启转写
     */
    var transcribe: Int = 0,

    /**
     * 是否开启字幕
     */
    var subtitle: Int = 0,
) {
    /**
     * 已用的体验时间，接收使用,请求的时候不需要
     */
    var duration: Int = 0

    companion object {
        fun formatUseData(openRttConversion: Boolean, openSubtitle: Boolean, sourceLan: String, targetLan: Array<String>): FcrRttChangeOptionsData {
            return FcrRttChangeOptionsData(RttChangOptionsLanguage(sourceLan, targetLan), if (openRttConversion) 1 else 0, if (openSubtitle) 1 else 0)
        }
    }
}

/**
 * Rtt-字幕逻辑管理
 */
private class RttSubtitlesManager(private val rttOptionsManager: RttOptionsManager, private val listener: FcrRttOptionsStatusListener) {

    /**
     * 是否开启成功
     */
    private var openSuccess = false

    /**
     * 字幕开启成功
     */
    private val messageWhatOpenSuccess = 0

    /**
     * 无人讲话的消息类型
     */
    private val messageWhatNoSpeaking = 1

    /**
     * 正在聆听的消息类型
     */
    private val messageWhatListening = 2

    /**
     * 隐藏rtt的消息类型
     */
    private val messageWhatHidde = 3

    /**
     * 所有的定时相关的处理
     */
    private val handler: Handler = object : Handler(rttOptionsManager.rttOptions.getActivityContext().mainLooper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                messageWhatOpenSuccess -> {
                    openSuccess = true
                    listener.subtitlesStateChange(true)
                    listener.audioStateSpeaking()
                    //显示两秒正在聆听
                    sendEmptyMessageDelayed(messageWhatListening, 2000)
                }

                messageWhatListening -> {
                    listener.audioStateNoSpeaking()
                    //显示两秒正在聆听
                    sendEmptyMessageDelayed(messageWhatNoSpeaking, 2000)
                }

                messageWhatNoSpeaking -> {
                    listener.audioStateNoSpeakingMoreTime()
                }

                messageWhatHidde -> {
                    listener.subtitlesViewReset(false)
                    listener.subtitlesStateChange(false)
                }
            }
        }
    }

    /**
     * 开启字幕
     * @param configAllowUse 配置是否允许rtt
     * @param experienceDefaultTime 体验默认时间
     * @param experienceReduceTime 体验剩余时间
     */
    fun openSubtitles(configAllowUse: Boolean, experienceDefaultTime: Int, experienceReduceTime: Int) {
        //清除消息
        this.clearHandlerMsg()
        listener.subtitlesViewReset(true)
        if (rttOptionsManager.isAllowUseRtt()) {
            //可以使用的话先隐藏体验，后续再根据条件判断是否显示
            listener.experienceInfoChange(configAllowUse, experienceDefaultTime, experienceReduceTime)
            //显示文案
            listener.audioStateOpening()
            if (openSuccess) {
                listener.audioStateNoSpeakingMoreTime()
            } else {
                //发起开启rtt请求
                rttOptionsManager.sendRequest(rttOptionsManager.isOpenConversion(), openRttSubtitles = true,
                    callback = object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
                        override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                            //延迟两秒开启文案：点击字幕位置可以更改字幕设置
                            listener.audioStateShowSettingHint()
                            rttOptionsManager.startExperience()
                            handler.sendEmptyMessageDelayed(messageWhatOpenSuccess, 2000)
                        }

                        override fun onError(httpCode: Int, code: Int, message: String?) {
                            openSuccess = false
                            listener.subtitlesStateChange(false)
                        }
                    })
            }
        } else {
            listener.experienceInfoChange(configAllowUse, experienceDefaultTime, experienceReduceTime)
            listener.audioStateNotAllowUse()
            openSuccess = false
            handler.sendEmptyMessageDelayed(messageWhatHidde, 2000)
        }
    }

    /**
     * 关闭字幕
     */
    fun closeSubtitles() {
        //清除消息
        this.clearHandlerMsg()
        //发起开启rtt请求
        if (openSuccess) {
            rttOptionsManager.sendRequest(rttOptionsManager.isOpenConversion(), false, object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
                override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                    openSuccess = false
                    listener.subtitlesStateChange(false)
                    if (!rttOptionsManager.isOpenConversion()) {
                        rttOptionsManager.stopExperience()
                    }
                }

                override fun onError(httpCode: Int, code: Int, message: String?) {
                }
            })
        }
        openSuccess = false
        listener.subtitlesViewReset(false)
    }

    /**
     * 是否开启了字幕
     */
    fun isOpenSubtitles(): Boolean {
        return openSuccess
    }

    /**
     * 初始化字幕状态
     */
    fun initOpenSubtitle(state: Boolean) {
        openSuccess = state
        if (openSuccess) {
            listener.subtitlesViewReset(true)
            rttOptionsManager.startExperience()
            //显示文案
            listener.audioStateOpening()
            handler.sendEmptyMessage(messageWhatOpenSuccess)
        }
    }

    /**
     * 设置当前翻译数据
     */
    fun setShowCurrentData(recordItem: RttRecordItem?, currentSettingInfo: RttSettingInfo) {
        if (isOpenSubtitles()) {
            listener.subtitlesViewReset(true)
            //清除消息
            this.clearHandlerMsg()

            val showText = recordItem?.getShowText()
            val translating = 2 == (showText?.size ?: 0) && showText?.get(0).isNullOrEmpty() && showText?.get(1).isNullOrEmpty()
            if (2 != (showText?.size ?: 0) || showText?.get(0).isNullOrEmpty() && !translating) {
                listener.audioStateNoSpeaking()
            } else if (translating) {
                listener.audioStateSpeaking()
            } else {
                listener.onMessageChange(recordItem)
//                rttBottomCenterSubtitlesView?.setShowTranslatorsInfo(recordItem?.userHeader ?: "", recordItem?.userName ?: "", sourceText ?: "",
//                    targetText)
            }
            //房间内有音频输出时，字幕区域显示对应转写文字，无音频输出时，字幕区域在音频停止3S后自动消失
            handler.sendEmptyMessageDelayed(messageWhatNoSpeaking, 3000)
        }
    }

    /**
     * 清除定时handler消息
     */
    private fun clearHandlerMsg() {
        handler.removeMessages(messageWhatOpenSuccess)
        handler.removeMessages(messageWhatListening)
        handler.removeMessages(messageWhatNoSpeaking)
        handler.removeMessages(messageWhatHidde)
    }
}

/**
 * Rtt-转写逻辑管理
 */
private class RttConversionManager(private val rttOptionsManager: RttOptionsManager, private val listener: FcrRttOptionsStatusListener) {

    /**
     * 是否开启成功
     */
    private var openSuccess = false

    /**
     * Rtt转写弹窗
     */
    private val rttConversionDialog by lazy {
        AgoraUIRttConversionDialogBuilder(rttOptionsManager.rttOptions.getActivityContext()).build().apply {
//            setOnDismissListener {
//                this.optionsCallback!!.closeConversion()
//            }
            optionsCallback = object : ConversionOptionsInterface {
                /**
                 * 开启转写
                 */
                override fun openConversion() {
                    if (rttOptionsManager.isOpenConversion()) {
                        openSuccess = true
                        listener.conversionStateChange(true)
                        rttOptionsManager.startExperience()
                    } else {
                        if (!openSuccess) {
                            rttOptionsManager.sendRequest(true, rttOptionsManager.isOpenSubtitles(),
                                callback = object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
                                    override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                                        openSuccess = true
                                        listener.conversionStateChange(true)
                                        rttOptionsManager.startExperience()
                                    }

                                    override fun onError(httpCode: Int, code: Int, message: String?) {
                                        openSuccess = false
                                        listener.conversionStateChange(false)
                                    }
                                })
                        } else {
                            listener.conversionStateChange(true)
                        }
                    }

                }

                /**
                 * 关闭转写
                 */
                override fun closeConversion() {
                    if (rttOptionsManager.isOpenConversion()) {
                        if (openSuccess) {
                            rttOptionsManager.sendRequest(false, rttOptionsManager.isOpenSubtitles(),
                                callback = object : HttpCallback<HttpBaseRes<RttChangeOptionsRes>>() {
                                    override fun onSuccess(result: HttpBaseRes<RttChangeOptionsRes>?) {
                                        openSuccess = false
                                        listener.conversionStateChange(false)
                                        rttOptionsManager.stopExperience()
                                    }

                                    override fun onError(httpCode: Int, code: Int, message: String?) {
                                    }
                                })
                        } else {
                            listener.conversionStateChange(false)
                        }
                    } else {
                        openSuccess = false
                        listener.conversionStateChange(false)
                    }
                }

                /**
                 * 打开设置页面
                 */
                override fun openSetting() {
                    rttOptionsManager.openSetting()
                }

            }
        }
    }

    /**
     * 开启转写
     */
    fun openConversion(list: List<RttRecordItem>) {
        listener.conversionViewReset()
        rttConversionDialog.show(list)
        if (rttOptionsManager.isAllowUseRtt()) {
            rttConversionDialog.optionsCallback!!.openConversion()
        }
    }

    /**
     * 关闭转写
     */
    fun closeConversion() {
        listener.conversionViewReset()
        if (rttOptionsManager.isAllowUseRtt()) {
            rttConversionDialog.dismiss()
            rttConversionDialog.optionsCallback!!.closeConversion()
        }
    }

    /**
     * 是否开启了转写
     */
    fun isOpenConversion(): Boolean {
        return openSuccess
    }

    /**
     * 初始化转写状态
     */
    fun initOpenConversion(state: Boolean) {
        openSuccess = state
        if (openSuccess) {
            listener.conversionViewReset()
        }
    }

    /**
     * 设置体验信息
     */
    fun setExperienceInfo(allowUseConfig: Boolean, rttExperienceReduceTime: Int) {
        rttConversionDialog.setExperienceInfo(allowUseConfig, rttExperienceReduceTime)
    }

    /**
     * 新增转写数据
     */
    fun updateShowList(list: List<RttRecordItem>) {
        try {
            rttConversionDialog.updateShowList(list)
        }catch (ignore:Exception){}
    }

    /**
     * 新增状态改变文本消息
     */
    fun addStateChangeTextMessage(transcribe: Int, userInfo: EduBaseUserInfo, localUserInfo: AgoraEduContextUserInfo?, useManager: RttUseManager) {
        val toOpen = 1 == transcribe
        val appContext = rttOptionsManager.rttOptions.getApplicationContext()
        val textContent = "${rttOptionsManager.formatRoleName(userInfo, localUserInfo)}${
            rttOptionsManager.rttOptions.getApplicationContext()
                .getString(if (toOpen) R.string.fcr_dialog_rtt_text_conversion_state_open else R.string.fcr_dialog_rtt_text_conversion_state_close)
        }"
        val toastContent = "${rttOptionsManager.formatRoleName(userInfo, localUserInfo)}${
            rttOptionsManager.rttOptions.getApplicationContext().getString(if (userInfo.userUuid == localUserInfo?.userUuid) {
                if (toOpen) R.string.fcr_dialog_rtt_toast_conversion_state_open_me_show else R.string.fcr_dialog_rtt_toast_conversion_state_close_me_show
            } else {
                if (toOpen) R.string.fcr_dialog_rtt_toast_conversion_state_open else R.string.fcr_dialog_rtt_toast_conversion_state_close
            })
        }"
        if (!rttOptionsManager.isOpenConversion() && toOpen) {
            rttOptionsManager.rttOptions.runOnUiThread{
                AgoraUIDialogBuilder(rttOptionsManager.rttOptions.getActivityContext()).title(appContext.resources.getString(R.string.fcr_dialog_rtt_conversion))
                    .message(textContent).messagePaddingHorizontal(appContext.resources.getDimensionPixelOffset(R.dimen.dp_10))
                    .negativeText(appContext.resources.getString(R.string.fcr_dialog_rtt_oher_change_source_hint_cancel))
                    .positiveText(appContext.resources.getString(R.string.fcr_dialog_rtt_oher_change_source_hint_confirm)).positiveClick {
                        openConversion(useManager.getShowRecordList())
                    }.build().show()
            }
        }
        AgoraUIToast.showDefaultToast(appContext, toastContent)
        useManager.disposeDataChangeSourceLanguage(userInfo, textContent)
        rttOptionsManager.rttOptions.runOnUiThread { updateShowList(useManager.getShowRecordList()) }
        LogX.i(TAG, "changeConversionState result=$textContent}")
    }

    /**
     * 新增目标文本改变消息
     */
    fun addTargetLanguageChangeMessage(open: Boolean, userInfo: EduBaseUserInfo, useManager: RttUseManager) {
        val showContent = rttOptionsManager.rttOptions.getApplicationContext().getString(R.string.fcr_dialog_rtt_text_open_target_language)
        useManager.disposeDataChangeSourceLanguage(userInfo, showContent)
        rttOptionsManager.rttOptions.runOnUiThread { updateShowList(useManager.getShowRecordList()) }
        LogX.i(TAG, "changeToOpenTargetLanguage result=$showContent}")
    }

    /**
     * 新增声源语言改变消息
     */
    fun addSourceLanguageChangeMessage(
        userInfo: EduBaseUserInfo, localUserInfo: AgoraEduContextUserInfo?, useManager: RttUseManager,
        settingsManager: RttSettingManager, sourceEnum: RttLanguageEnum,
    ) {
        //前置名称
        val useText = "${
            rttOptionsManager.formatRoleName(userInfo, localUserInfo)
        }${rttOptionsManager.rttOptions.getApplicationContext().getString(R.string.fcr_dialog_rtt_text_change_source_language)}"
        val languageText = rttOptionsManager.rttOptions.getApplicationContext().getString(sourceEnum.textRes)
        val showContent = SpannableString("$useText${languageText}")
        showContent.setSpan(ForegroundColorSpan(ContextCompat.getColor(rttOptionsManager.rttOptions.getApplicationContext(), R.color.fcr_blue_4262)),
            showContent.lastIndexOf(languageText), showContent.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        AgoraUIToast.showDefaultToast(rttOptionsManager.rttOptions.getApplicationContext(), showContent)
        //通知处理
        useManager.disposeDataChangeSourceLanguage(userInfo, showContent.toString())
        updateShowList(useManager.getShowRecordList())
        LogX.i(TAG, "changeSourceLanguage source=${settingsManager.currentSettingInfo.getSourceLan()}, result=$sourceEnum}")

    }
}

/**
 * Rtt-设置弹窗管理
 */
private class RttSettingManager(private val rttOptionsManager: RttOptionsManager) {
    /**
     * RTT/转写设置弹窗
     */
    private val rttSettingDialog by lazy {
        AgoraUIRttSettingBuilder(rttOptionsManager.rttOptions.getActivityContext()).setListener(object : AgoraUIRttSettingDialogListener {
            /**
             * 修改双语显示
             */
            override fun changeDoubleShow(showDouble: Boolean) {
                currentSettingInfo.setShowDoubleLan(showDouble)
                rttOptionsManager.changeDoubleShow(showDouble)
            }

            /**
             * 设置目标语言
             */
            override fun setTargetLan(code: String) {
                RttLanguageEnum.values().find { it.value == code }?.let {
                    rttOptionsManager.setTargetLanguage(it)
                }
            }

            /**
             * 设置声源语言
             */
            override fun setSourceLan(code: String) {
                RttLanguageEnum.values().find { it.value == code }?.let {
                    rttOptionsManager.setSourceLanguage(it)
                }
            }
        }).build()
    }

    /**
     * 当前语言的管理信息
     */
    val currentSettingInfo by lazy { RttSettingInfo(rttOptionsManager) }

    /**
     * 开启设置
     */
    fun openSetting() {
        if (rttOptionsManager.isAllowUseRtt()) {
            rttSettingDialog.show(currentSettingInfo)
        }
    }

    /**
     * 关闭设置
     */
    fun closeSetting() {
        rttSettingDialog.dismiss()
    }
}

/**
 * Rtt-使用管理
 * @param configAllowUse RTT功能是否允许使用，默认不可以使用，不可使用的话则提供体验时间
 */
private class RttUseManager(
    private val rttOptionsManager: RttOptionsManager, var configAllowUse: Boolean = false,
    private val listener: FcrRttOptionsStatusListener,
) {
    /**
     * rtt功能默认体验时间,默认十分钟
     */
    val rttExperienceDefaultTime: Int = 600000

    /**
     * rtt功能剩余体验时间,默认十分钟
     */
    var rttExperienceReduceTime: Int = rttExperienceDefaultTime
        private set

    /**
     * 所有的定时相关的处理
     */
    private val handler: Handler = object : Handler(rttOptionsManager.rttOptions.getActivityContext().mainLooper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            rttExperienceReduceTime -= 1000
            listener.experienceInfoChange(configAllowUse, rttExperienceDefaultTime, rttExperienceReduceTime)
            if (rttExperienceReduceTime <= 0) {
                listener.audioStateNotAllowUse()
                stopExperience()
            } else {
                sendEmptyMessageDelayed(msg.what, 1000)
            }
        }
    }

    /**
     * 总记录数据列表
     */
    private val allRecordList = arrayListOf<RttRecordItem>()

    /**
     * 转写显示的记录列表
     */
    private val showRecordList = arrayListOf<RttRecordItem>()

    /**
     * 最后一条信息
     */
    private var lastRecord: RttRecordItem? = null

    /**
     * 是否允许体验
     */
    fun isAllowUse(): Boolean {
        return this.configAllowUse || this.rttExperienceReduceTime > 0
    }

    /**
     * 设置体验剩余时间
     */
    fun setExperienceReduceTime(time: Int) {
        this.rttExperienceReduceTime = time.coerceAtMost(this.rttExperienceReduceTime).coerceAtLeast(0)
    }

    /**
     * 开始体验
     */
    fun startExperience() {
        if (isAllowUse() && rttExperienceReduceTime > 0) {
            listener.experienceInfoChange(configAllowUse, rttExperienceDefaultTime, rttExperienceReduceTime)
            handler.removeMessages(0)
            handler.sendEmptyMessageDelayed(0, 1000)
        }
    }

    /**
     * 结束体验
     */
    fun stopExperience() {
        handler.removeMessages(0)
    }

    /**
     * 声源语言调整数据处理
     */
    fun disposeDataChangeSourceLanguage(userInfo: EduBaseUserInfo, text: String): RttRecordItem {
        return RttRecordItem().apply {
            uuid = UUID.randomUUID().toString()
            userName = userInfo.userName
            statusText = text
            if (allRecordList.isEmpty() || allRecordList.isNotEmpty() && text != allRecordList[allRecordList.size - 1].statusText) {
                allRecordList.add(this)
                showRecordList.add(this)
            }
        }
    }

    /**
     * 处理数据
     */
    fun disposeData(rttMsgData: AgoraSpeech2TextProtobuffer.Text, settingInfo: RttSettingInfo, streamContext: StreamContext?): RttRecordItem? {
        val lastItem = (if (allRecordList.isEmpty()) null else allRecordList[allRecordList.size - 1])
        val lastItemByUid = lastItem?.uid
        var paramsData: RttRecordItem? = null
        when (rttMsgData.dataType) {
            //转写
            "transcribe" -> {
                val sourceTextStr = StringBuffer()
                var final = false
                var confidence = 0.0
                rttMsgData.wordsList.forEach { word ->
                    sourceTextStr.append(word.text)
                    final = word.isFinal
                    confidence = word.confidence
                }
                LogX.i(TAG, "transcribe: $lastItemByUid$$$$$$$$sourceTextStr")
                if (lastItemByUid == null || rttMsgData.uid != lastItemByUid || lastItem.isFinal) {
                    paramsData = RttRecordItem().apply {
                        uuid = UUID.randomUUID().toString()
                        currentTargetLan = settingInfo.getTargetLan()
                        showDoubleLan = settingInfo.isShowDoubleLan()
                        sourceLan = RttLanguageEnum.values().find { it.value == rttMsgData.culture }
                        sourceText = sourceTextStr.toString()
                        uid = rttMsgData.uid
                        time = if (final) if (rttMsgData.time == 0L) rttMsgData.time else System.currentTimeMillis() else rttMsgData.time
                        isFinal = final
                        sourceConfidence = confidence
                    }
                    allRecordList.add(paramsData)
                } else {
                    paramsData = allRecordList.findLast { it.uid == rttMsgData.uid }?.apply {
                        uuid = UUID.randomUUID().toString()
                        currentTargetLan = settingInfo.getTargetLan()
                        showDoubleLan = settingInfo.isShowDoubleLan()
                        sourceText = sourceTextStr.toString()
                        time = if (final) if (rttMsgData.time == 0L) rttMsgData.time else System.currentTimeMillis() else rttMsgData.time
                        isFinal = final
                        sourceConfidence = confidence
                    }
                }
                sourceTextStr.setLength(0)
            }
            //翻译
            "translate" -> {
                LogX.i(TAG, "Translation:" + GsonUtil.toJson(rttMsgData))
                val last = allRecordList.findLast { it.uid == lastItemByUid }
                val tranList = last?.targetInfo ?: arrayListOf()
                val transTextStr = StringBuffer()
                rttMsgData.transList.forEach { transItem ->
                    transItem.textsList.forEach { text ->
                        LogX.i(TAG, "Translation:$lastItemByUid$$$$$$$$$text")
                        transTextStr.append(text)
                    }
                    tranList.find { transItem.lang == it.language?.value }.let {find->
                        if(find != null){
                            find.text = transTextStr.toString()
                        }else{
                            tranList.add(RttRecordItemTran().apply {
                                language = RttLanguageEnum.values().find { it.value == transItem.lang }
                                text = transTextStr.toString()
                            })
                        }
                    }
                    transTextStr.setLength(0)
                }

                paramsData = allRecordList.findLast { it.uid == lastItemByUid }?.apply {
                    currentTargetLan = settingInfo.getTargetLan()
                    uuid = UUID.randomUUID().toString()
                    showDoubleLan = settingInfo.isShowDoubleLan()
                    targetInfo = tranList
                    targetText = tranList.find { item-> (item.language?.value ?:"") == currentTargetLan?.value }?.text
                }
            }
        }
        lastRecord = paramsData
        if (lastRecord != null && rttOptionsManager.isOpenConversion()) {
            val showItem = if (showRecordList.isNotEmpty()) showRecordList[showRecordList.size - 1] else null
            if (showItem?.uuid != null && showItem.uuid == lastRecord!!.uuid) {
                showRecordList[showRecordList.size - 1] = lastRecord!!
            } else {
                showRecordList.add(lastRecord!!)
            }
        }
        //格式化所有的用户信息
        formatAllUserInfo(streamContext)
        return paramsData
    }


    /**
     * 格式化所有的用户信息
     */
    fun formatAllUserInfo(streamContext: StreamContext?) {
        allRecordList.forEach { item ->
            streamContext?.getAllStreamList()?.find { it.streamUuid == item.uid?.toString() }?.owner?.let { info ->
                item.userName = info.userName
                item.userHeader = info.userName
            }
        }
        showRecordList.forEach { item ->
            streamContext?.getAllStreamList()?.find { it.streamUuid == item.uid?.toString() }?.owner?.let { info ->
                item.userName = info.userName
                item.userHeader = info.userName
            }
        }
        lastRecord?.let { item ->
            streamContext?.getAllStreamList()?.find { it.streamUuid == item.uid?.toString() }?.owner?.let { info ->
                item.userName = info.userName
                item.userHeader = info.userName
            }
        }
    }

    /**
     * 获取记录列表
     */
    fun getShowRecordList(): List<RttRecordItem> {
        return showRecordList.toList()
    }

    /**
     * 设置最后一条信息为最后
     */
    fun setLastFinal() {
        if (allRecordList.isNotEmpty()) {
            allRecordList[allRecordList.size - 1].isFinal = true
        }
        if (showRecordList.isNotEmpty()) {
            showRecordList[showRecordList.size - 1].isFinal = true
        }
        lastRecord?.isFinal = true
    }

}

/**
 * Rtt操作状态监听
 */
abstract class FcrRttOptionsStatusListener {
    /**
     * rtt功能状态变更
     * @param open 开启-true，关闭-false
     */
    open fun rttStateChange(open: Boolean) {}

    /**
     * 音频状态-无人讲话
     */
    open fun audioStateNoSpeaking() {}

    /**
     * 音频状态-有人讲话
     */
    open fun audioStateSpeaking() {}

    /**
     * 音频状态-超过一定时间无人讲话
     */
    open fun audioStateNoSpeakingMoreTime() {}

    /**
     * 音频状态-开启中
     */
    open fun audioStateOpening() {}

    /**
     * 音频状态-无法使用
     */
    open fun audioStateNotAllowUse() {}

    /**
     * 音频状态-显示设置提示
     */
    open fun audioStateShowSettingHint() {}

    /**
     * 体验信息变更
     * @param configAllowUseRtt 配置是否可以使用rtt功能
     * @param experienceReduceTime 剩余体验时间
     * @param experienceDefaultTime 默认体验时间
     */
    open fun experienceInfoChange(configAllowUseRtt: Boolean, experienceDefaultTime: Int, experienceReduceTime: Int) {}

    /**
     * 字幕状态变更
     * @param toOpen 开启-true，关闭-false
     */
    open fun subtitlesStateChange(toOpen: Boolean) {}

    /**
     * 字幕视图重置
     */
    open fun subtitlesViewReset(openSuccess: Boolean) {}

    /**
     * 转写状态变更
     * @param toOpen 开启-true，关闭-false
     */
    open fun conversionStateChange(toOpen: Boolean) {}

    /**
     * 转写视图重置
     */
    open fun conversionViewReset() {}

    /**
     * 声源语言修改
     */
    open fun sourceLanguageChange(language: RttLanguageEnum) {}

    /**
     * 目标语言修改-网络请求结果
     */
    open fun targetLanguageChange(languages: List<RttLanguageEnum>) {}

    /**
     * 双语状态变更
     * @param open 开启-true，关闭-false
     */
    open fun showDoubleLanguage(open: Boolean) {}

    /**
     * 双语状态变更-网络请求结果
     * @param open 开启-true，关闭-false
     */
    open fun showDoubleLanguageNetResult(open: Boolean) {}

    /**
     * 消息改变
     * @param currentData 当前要显示的数据
     */
    open fun onMessageChange(currentData: RttRecordItem?) {}
}
