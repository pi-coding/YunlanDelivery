package com.fastdine.controller.notify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fastdine.properties.WeChatProperties;
import com.fastdine.service.OrderService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调
 * <p>
 * 微信支付成功后由微信服务器主动调用，不属于 /admin 或 /user，不经过 JWT 拦截器。
 * 这里必须把结果原样回给微信，否则微信会一直重推。
 * <p>
 * 本机 fastdine.wechat.pay-enabled=false（没有商户号），真实支付走的是本地模拟，
 * 这个回调不会被触发；代码保留，配好商户信息并开启 pay-enabled 后即可直接生效。
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private WeChatProperties weChatProperties;

    @Value("${fastdine.wechat.pay-enabled:false}")
    private boolean payEnabled;

    /**
     * 支付成功回调
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = readData(request);
        log.info("收到支付成功回调，原始报文长度：{}", body == null ? 0 : body.length());

        // 没开真实支付时，收到回调说明配置不对，直接回 SUCCESS 避免微信无限重推
        if (!payEnabled) {
            log.warn("收到支付回调但 fastdine.wechat.pay-enabled=false，已忽略。报文：{}", body);
            responseToWeixin(response);
            return;
        }

        String plainText = decryptData(body);
        log.info("支付回调解密后：{}", plainText);

        JSONObject jsonObject = JSON.parseObject(plainText);
        String outTradeNo = jsonObject.getString("out_trade_no");
        String transactionId = jsonObject.getString("transaction_id");
        log.info("商户订单号：{}，微信支付交易号：{}", outTradeNo, transactionId);

        // 改订单状态 + 来单提醒，paySuccess 内部会处理幂等
        orderService.paySuccess(outTradeNo);

        responseToWeixin(response);
    }

    /**
     * 读取请求体
     */
    private String readData(HttpServletRequest request) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (result.length() > 0) {
                    result.append("\n");
                }
                result.append(line);
            }
        }
        return result.toString();
    }

    /**
     * 用 APIv3 密钥解密密文
     */
    private String decryptData(String body) throws Exception {
        String apiV3Key = weChatProperties.getApiV3Key();
        if (apiV3Key == null || apiV3Key.isEmpty()) {
            throw new IllegalStateException("未配置 fastdine.wechat.api-v3-key，无法解密支付回调");
        }

        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(apiV3Key.getBytes(StandardCharsets.UTF_8));
        return aesUtil.decryptToString(
                associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);
    }

    /**
     * 按微信要求的格式应答，否则微信会持续重推
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json");
        Map<String, Object> map = new HashMap<>(2);
        map.put("code", "SUCCESS");
        map.put("message", "SUCCESS");
        response.getOutputStream().write(JSON.toJSONString(map).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
