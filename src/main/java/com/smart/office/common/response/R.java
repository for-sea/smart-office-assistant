package com.smart.office.common.response;

import java.io.Serializable;

/**
 * 统一数据返回实体类
 */
public class R<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功返回
     */
    public static <T> R<T> success() {
        return new R<>(200, "success", null);
    }

    /**
     * 成功返回带数据
     */
    public static <T> R<T> success(T data) {
        return new R<>(200, "success", data);
    }

    /**
     * 成功返回带消息和数据
     */
    public static <T> R<T> success(String message, T data) {
        return new R<>(200, message, data);
    }

    /**
     * 失败返回
     */
    public static <T> R<T> fail() {
        return new R<>(500, "fail", null);
    }

    /**
     * 失败返回带消息
     */
    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null);
    }

    /**
     * 失败返回带代码和消息
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}