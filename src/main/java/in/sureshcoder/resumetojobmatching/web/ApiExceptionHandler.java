package in.sureshcoder.resumetojobmatching.web;

import in.sureshcoder.resumetojobmatching.jev.JevApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    /**
     * Surfaces Jev failures as 502 rather than 500: the request was fine, the upstream model call
     * was not, and that distinction is what tells a caller whether retrying is worthwhile.
     */
    @ExceptionHandler(JevApiException.class)
    ProblemDetail handleJevFailure(JevApiException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Jev evaluation failed");
        problem.setProperty("upstreamStatus", e.statusCode());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}
