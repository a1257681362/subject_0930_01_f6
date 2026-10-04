package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 人工异常标记/确认请求（V6：只追加异常事件，不改动任何已有记录/凭证）。
 *
 * 时间口径：
 * - occurredTime  异常实际发生时间（可补录为过去时刻）；
 * - firstFoundTime 首次发现时间（可补录，缺省取 occurredTime），一旦落库永不修改；
 * - 处置时钟起点 confirmedTime=本次确认登记时刻（服务端取当前时间，客户端不可指定、不可补改）。
 */
public class ExceptionMarkRequest {
    @NotNull(message = "运输批次ID不能为空")
    private Long batchId;
    /** TEMP_EXCURSION / SEAL_BROKEN / DELAY / RECEIVER_REJECT / DAMAGE / OTHER */
    @NotBlank(message = "异常类型不能为空")
    private String exceptionType;
    @NotBlank(message = "异常描述不能为空")
    private String description;
    /** 异常发生时间（可补录）；缺省取当前时间 */
    private LocalDateTime occurredTime;
    /** 首次发现时间（可补录）；缺省取 occurredTime，落库后不可覆盖 */
    private LocalDateTime firstFoundTime;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getOccurredTime() { return occurredTime; }
    public void setOccurredTime(LocalDateTime occurredTime) { this.occurredTime = occurredTime; }
    public LocalDateTime getFirstFoundTime() { return firstFoundTime; }
    public void setFirstFoundTime(LocalDateTime firstFoundTime) { this.firstFoundTime = firstFoundTime; }
}
