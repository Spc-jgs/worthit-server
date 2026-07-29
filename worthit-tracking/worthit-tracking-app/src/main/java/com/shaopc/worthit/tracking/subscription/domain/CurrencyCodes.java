package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 业务逻辑需要识别的 ISO 4217 币种编码。
 *
 * <p>公网仍允许任意三位币种，本类只承载具有特殊业务语义的编码。
 */
public final class CurrencyCodes {

    /** 人民币。 */
    public static final String CNY = "CNY";

    private CurrencyCodes() {
    }
}
