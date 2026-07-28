package com.shaopc.worthit.reminder.app.reconcile.infrastructure.persistence;

/**
 * rem_command_log 查询对象。
 */
public class ReminderCommandLogDO {

    private Long id;
    private String eventId;
    private Long bindingId;
    private Long sourceVersion;
    private String payloadDigest;
    private String resultCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
    }

    public Long getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(Long sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public void setPayloadDigest(String payloadDigest) {
        this.payloadDigest = payloadDigest;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }
}
