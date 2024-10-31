package com.qny.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用的结果返回类
 *
 * @param <T>
 */
@Data
public class Response<T> implements Serializable {

    private Integer code;

    private String errorMessage;

    private T data;

    public Response() {
        this.code = 200;
    }

    public Response(Integer code, T data) {
        this.code = code;
        this.data = data;
    }

    public Response(Integer code, String msg, T data) {
        this.code = code;
        this.errorMessage = msg;
        this.data = data;
    }

    public Response(Integer code, String msg) {
        this.code = code;
        this.errorMessage = msg;
    }

    public static Response errorResult(int code, String msg) {
        Response result = new Response();
        return result.error(code, msg);
    }

    public static Response okResult(int code, String msg) {
        Response result = new Response();
        return result.ok(code, null, msg);
    }

    public static Response okResult(Object data) {
        Response result = setAppHttpCodeEnum(AppHttpCodeEnum.SUCCESS, AppHttpCodeEnum.SUCCESS.getErrorMessage());
        if (data != null) {
            result.setData(data);
        }
        return result;
    }

    public static Response errorResult(AppHttpCodeEnum enums) {
        return setAppHttpCodeEnum(enums, enums.getErrorMessage());
    }

    public static Response errorResult(AppHttpCodeEnum enums, String errorMessage) {
        return setAppHttpCodeEnum(enums, errorMessage);
    }

    public static Response setAppHttpCodeEnum(AppHttpCodeEnum enums) {
        return okResult(enums.getCode(), enums.getErrorMessage());
    }

    private static Response setAppHttpCodeEnum(AppHttpCodeEnum enums, String errorMessage) {
        return okResult(enums.getCode(), errorMessage);
    }

    public Response<?> error(Integer code, String msg) {
        this.code = code;
        this.errorMessage = msg;
        return this;
    }

    public Response<?> ok(Integer code, T data) {
        this.code = code;
        this.data = data;
        return this;
    }

    public Response<?> ok(Integer code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.errorMessage = msg;
        return this;
    }

    public Response<?> ok(T data) {
        this.data = data;
        return this;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }


}

