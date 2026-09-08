package io.aioa.common.web;

import io.aioa.common.api.BizException;
import io.aioa.common.api.ErrorCode;
import io.aioa.common.api.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException ex, HttpServletRequest req) {
        String traceId = traceId(req);
        log.warn("[biz] trace={} code={} msg={}", traceId, ex.getCode(), ex.getMessage());
        HttpStatus http = ex.getCode() == ErrorCode.UNAUTHORIZED.code()
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.OK;
        R<Void> r = R.fail(ex.getCode(), ex.getMessage());
        r.setTraceId(traceId);
        return ResponseEntity.status(http).body(r);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> handleValid(Exception ex, HttpServletRequest req) {
        String traceId = traceId(req);
        log.warn("[valid] trace={} msg={}", traceId, ex.getMessage());
        R<Void> r = R.fail(ErrorCode.BAD_REQUEST.code(), ErrorCode.BAD_REQUEST.message());
        r.setTraceId(traceId);
        return ResponseEntity.badRequest().body(r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception ex, HttpServletRequest req) {
        String traceId = traceId(req);
        log.error("[unknown] trace={}", traceId, ex);
        R<Void> r = R.fail(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message());
        r.setTraceId(traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

    private String traceId(HttpServletRequest req) {
        String header = req.getHeader("X-Trace-Id");
        return header != null ? header : UUID.randomUUID().toString().replace("-", "");
    }
}
