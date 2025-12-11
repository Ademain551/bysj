package com.dlu.mtjbysj.shop;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AlipayConfig {

    @Value("${alipay.gateway-url}")
    private String gatewayUrl;

    @Value("${alipay.app-id}")
    private String appId;

    @Value("${alipay.merchant-private-key}")
    private String merchantPrivateKey;

    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${alipay.sign-type:RSA2}")
    private String signType;

    @Value("${alipay.charset:utf-8}")
    private String charset;

    @Value("${alipay.return-url}")
    private String returnUrl;

    @Value("${alipay.notify-url:}")
    private String notifyUrl;

    @Value("${alipay.frontend-return-base:http://localhost:5173}")
    private String frontendReturnBase;

    private volatile AlipayClient client;

    public AlipayClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new DefaultAlipayClient(
                            gatewayUrl,
                            appId,
                            merchantPrivateKey,
                            "json",
                            charset,
                            alipayPublicKey,
                            signType
                    );
                }
            }
        }
        return client;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public String getSignType() {
        return signType;
    }

    public String getCharset() {
        return charset;
    }

    public String getFrontendReturnBase() {
        return frontendReturnBase;
    }
}
