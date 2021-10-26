package com.ejlchina.okhttps.okhttp;

import com.ejlchina.okhttps.*;
import com.ejlchina.okhttps.internal.*;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executor;


public class OkHttpBuilderImpl implements HTTP.Builder {

    private OkHttpClient okClient;

    private String baseUrl;

    private final List<HTTP.OkConfig> configs;

    private final Map<String, String> mediaTypes;

    private final List<String> contentTypes;

    private final List<MsgConvertor> msgConvertors;

    private final List<Preprocessor> preprocessors;

    private int preprocTimeoutTimes = 10;

    private Executor mainExecutor;

    private Scheduler taskScheduler;

    private DownListener downloadListener;

    private TaskListener<HttpResult> responseListener;

    private TaskListener<IOException> exceptionListener;

    private TaskListener<HttpResult.State> completeListener;

    private Charset charset = StandardCharsets.UTF_8;

    private String bodyType = OkHttps.FORM;


    public OkHttpBuilderImpl() {
        mediaTypes = new HashMap<>();
        mediaTypes.put("*", "application/octet-stream");
        mediaTypes.put("png", "image/png");
        mediaTypes.put("jpg", "image/jpeg");
        mediaTypes.put("jpeg", "image/jpeg");
        mediaTypes.put("wav", "audio/wav");
        mediaTypes.put("mp3", "audio/mp3");
        mediaTypes.put("mp4", "video/mpeg4");
        mediaTypes.put("txt", "text/plain");
        mediaTypes.put("xls", "application/x-xls");
        mediaTypes.put("xml", "text/xml");
        mediaTypes.put("apk", "application/vnd.android.package-archive");
        mediaTypes.put("doc", "application/msword");
        mediaTypes.put("pdf", "application/pdf");
        mediaTypes.put("html", "text/html");
        contentTypes = new ArrayList<>();
        contentTypes.add("application/x-www-form-urlencoded; charset={charset}");
        contentTypes.add("application/json; charset={charset}");
        contentTypes.add("application/xml; charset={charset}");
        contentTypes.add("application/protobuf");
        contentTypes.add("application/msgpack");
        preprocessors = new ArrayList<>();
        msgConvertors = new ArrayList<>();
        configs = new ArrayList<>();
    }

    public OkHttpBuilderImpl(OkHttpClientWrapper hc) {
        okClient = hc.okClient();
        baseUrl = hc.baseUrl();
        mediaTypes = hc.mediaTypes();
        preprocessors = new ArrayList<>();
        Collections.addAll(preprocessors, hc.preprocessors());
        TaskExecutor executor = hc.executor();
        contentTypes = new ArrayList<>();
        Collections.addAll(contentTypes, executor.getContentTypes());
        mainExecutor = executor.getMainExecutor();
        taskScheduler = executor.getTaskScheduler();
        downloadListener = executor.getDownloadListener();
        responseListener = executor.getResponseListener();
        exceptionListener = executor.getExceptionListener();
        completeListener = executor.getCompleteListener();
        msgConvertors = new ArrayList<>();
        Collections.addAll(msgConvertors, executor.getMsgConvertors());
        preprocTimeoutTimes = hc.preprocTimeoutTimes();
        configs = new ArrayList<>();
        bodyType = hc.bodyType();
        charset = hc.charset();
    }

    /**
     * 自 v3.2.0 后可以多次调用
     * 配置 OkHttpClient
     * @param config 配置器
     * @return Builder
     */
    public HTTP.Builder config(HTTP.OkConfig config) {
        if (config != null) {
            configs.add(config);
        }
        return this;
    }

    /**
     * 设置 baseUrl
     * @param baseUrl 全局URL前缀
     * @return Builder
     */
    public HTTP.Builder baseUrl(String baseUrl) {
        if (baseUrl != null) {
            this.baseUrl = baseUrl.trim();
        }
        return this;
    }

    /**
     * 配置媒体类型
     * @param mediaTypes 媒体类型
     * @return Builder
     */
    public HTTP.Builder mediaTypes(Map<String, String> mediaTypes) {
        if (mediaTypes != null) {
            this.mediaTypes.putAll(mediaTypes);
        }
        return this;
    }

    /**
     * 配置媒体类型
     * @param key 媒体类型KEY
     * @param value 媒体类型VALUE
     * @return Builder
     */
    public HTTP.Builder mediaTypes(String key, String value) {
        if (key != null && value != null) {
            this.mediaTypes.put(key, value);
        }
        return this;
    }

    /**
     * 配置支持的报文体类型
     * @param contentTypes 报文体类型列表
     * @return Builder
     */
    public HTTP.Builder contentTypes(List<String> contentTypes) {
        if (contentTypes != null) {
            this.contentTypes.addAll(contentTypes);
        }
        return this;
    }

    /**
     * 配置支持的报文体类型
     * @param contentType 报文体类型
     * @return Builder
     */
    public HTTP.Builder contentTypes(String contentType) {
        if (contentType != null) {
            this.contentTypes.add(contentType);
        }
        return this;
    }

    /**
     * 设置回调执行器，例如实现切换线程功能，只对异步请求有效
     * @param executor 回调执行器
     * @return Builder
     */
    public HTTP.Builder callbackExecutor(Executor executor) {
        this.mainExecutor = executor;
        return this;
    }

    /**
     * 配置 任务调度器，可用的调度由 {@link WebSocketTask#heatbeat(int, int) } 指定的心跳任务
     * 若不配置，则生成一个 线程容量为 1 的 ScheduledThreadPoolExecutor 调度器
     * @since v2.3.0
     * @param scheduler 调度器
     * @return Builder
     */
    public HTTP.Builder taskScheduler(Scheduler scheduler) {
        this.taskScheduler = scheduler;
        return this;
    }

    /**
     * 添加可并行处理请求任务的预处理器
     * @param preprocessor 预处理器
     * @return Builder
     */
    public HTTP.Builder addPreprocessor(Preprocessor preprocessor) {
        if (preprocessor != null) {
            preprocessors.add(preprocessor);
        }
        return this;
    }

    /**
     * 添加串行预处理器
     * @param preprocessor 预处理器
     * @return Builder
     */
    public HTTP.Builder addSerialPreprocessor(Preprocessor preprocessor) {
        if (preprocessor != null) {
            preprocessors.add(new SerialPreprocessor(preprocessor));
        }
        return this;
    }

    /**
     * 清空预处理器（包括串行预处理器）
     * @since v2.5.0
     * @return Builder
     */
    public HTTP.Builder clearPreprocessors() {
        preprocessors.clear();
        return this;
    }

    /**
     * 最大预处理时间（倍数，相当普通请求的超时时间）
     * @param times 普通超时时间的倍数，默认为 10
     * @return Builder
     */
    public HTTP.Builder preprocTimeoutTimes(int times) {
        if (times > 0) {
            preprocTimeoutTimes = times;
        }
        return this;
    }

    /**
     * 设置全局响应监听
     * @param listener 监听器
     * @return Builder
     */
    public HTTP.Builder responseListener(TaskListener<HttpResult> listener) {
        responseListener = listener;
        return this;
    }

    /**
     * 设置全局异常监听
     * @param listener 监听器
     * @return Builder
     */
    public HTTP.Builder exceptionListener(TaskListener<IOException> listener) {
        exceptionListener = listener;
        return this;
    }

    /**
     * 设置全局完成监听
     * @param listener 监听器
     * @return Builder
     */
    public HTTP.Builder completeListener(TaskListener<HttpResult.State> listener) {
        completeListener = listener;
        return this;
    }

    /**
     * 设置下载监听器
     * @param listener 监听器
     * @return Builder
     */
    public HTTP.Builder downloadListener(DownListener listener) {
        downloadListener = listener;
        return this;
    }

    /**
     * @since v2.0.0
     * 添加消息转换器
     * @param msgConvertor JSON 服务
     * @return Builder
     */
    public HTTP.Builder addMsgConvertor(MsgConvertor msgConvertor) {
        if (msgConvertor != null) {
            msgConvertors.add(msgConvertor);
        }
        return this;
    }

    /**
     * 清空消息转换器
     * @since v2.5.0
     * @return Builder
     */
    public HTTP.Builder clearMsgConvertors() {
        msgConvertors.clear();
        return this;
    }

    /**
     * @since v2.0.0
     * 设置默认编码格式
     * @param charset 编码
     * @return Builder
     */
    public HTTP.Builder charset(Charset charset) {
        if (charset != null) {
            this.charset = charset;
        }
        return this;
    }

    /**
     * @since v2.0.0
     * 设置默认请求体类型
     * @param bodyType 请求体类型
     * @return Builder
     */
    public HTTP.Builder bodyType(String bodyType) {
        if (bodyType != null) {
            this.bodyType = bodyType.toLowerCase();
        }
        return this;
    }

    /**
     * 构建 HTTP 实例
     * @return HTTP
     */
    public HTTP build() {
        if (configs.size() > 0 || okClient == null) {
            OkHttpClient.Builder builder;
            if (okClient != null) {
                builder = okClient.newBuilder();
            } else {
                builder = new OkHttpClient.Builder();
            }
            for (HTTP.OkConfig config: configs) {
                config.config(builder);
            }
            // fix issue: https://github.com/ejlchina/okhttps/issues/8
            if (needCopyInterceptor(builder.interceptors())) {
                builder.addInterceptor(new CopyInterceptor());
            }
            okClient = builder.build();
        } else if (needCopyInterceptor(okClient.interceptors())) {
            okClient = okClient.newBuilder()
                    .addInterceptor(new CopyInterceptor())
                    .build();
        }
        return new OkHttpClientWrapper(this);
    }

    private boolean needCopyInterceptor(List<Interceptor> list) {
        return mainExecutor != null && Platform.ANDROID_SDK_INT > 24 && CopyInterceptor.notIn(list);
    }

    public OkHttpClient okClient() {
        return okClient;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Map<String, String> getMediaTypes() {
        return mediaTypes;
    }

    public Executor mainExecutor() {
        return mainExecutor;
    }

    public Preprocessor[] preprocessors() {
        return preprocessors.toArray(new Preprocessor[0]);
    }

    public DownListener downloadListener() {
        return downloadListener;
    }

    public TaskListener<HttpResult> responseListener() {
        return responseListener;
    }

    public TaskListener<IOException> exceptionListener() {
        return exceptionListener;
    }

    public TaskListener<HttpResult.State> completeListener() {
        return completeListener;
    }

    public MsgConvertor[] msgConvertors() {
        return msgConvertors.toArray(new MsgConvertor[0]);
    }

    public Scheduler taskScheduler() {
        return taskScheduler;
    }

    public String[] contentTypes() {
        return contentTypes.toArray(new String[0]);
    }

    public int preprocTimeoutTimes() {
        return preprocTimeoutTimes;
    }

    public Charset charset() {
        return charset;
    }

    public String bodyType() {
        return bodyType;
    }

}