package com.infy.catalog.api.problem;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/**
 * Error body for every 4xx/5xx response (RFC 9457), served as application/problem+json,
 * application/problem+xml, text/html or text/plain depending on the Accept header.
 * Extension members: errorCode (stable, machine-readable), traceId, errors (per-field messages).
 * Never contains stack traces, SQL or class names.
 */
@XmlRootElement(name = "problem")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "problem", propOrder = {"type", "title", "status", "detail", "instance", "errorCode", "traceId", "errors"})
public class ProblemDetail {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String errorCode;
    private String traceId;

    @XmlElementWrapper(name = "errors")
    @XmlElement(name = "error")
    private List<String> errors;

    public ProblemDetail() {
        // for JAXB and Jackson
    }

    public ProblemDetail(String type, String title, int status, String detail, String instance,
                         String errorCode, String traceId, List<String> errors) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.errors = errors == null || errors.isEmpty() ? null : new ArrayList<>(errors);
    }

    public String getType() { return type; }
    public String getTitle() { return title; }
    public int getStatus() { return status; }
    public String getDetail() { return detail; }
    public String getInstance() { return instance; }
    public String getErrorCode() { return errorCode; }
    public String getTraceId() { return traceId; }
    public List<String> getErrors() { return errors == null ? List.of() : errors; }
}
