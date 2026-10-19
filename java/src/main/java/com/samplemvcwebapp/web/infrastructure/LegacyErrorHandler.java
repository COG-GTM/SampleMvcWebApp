package com.samplemvcwebapp.web.infrastructure;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/** Replaces the legacy global HandleErrorAttribute (App_Start/FilterConfig): failures render Views/Shared/Error. */
@ControllerAdvice
public class LegacyErrorHandler {

    @ExceptionHandler({ResourceNotFoundException.class, MethodArgumentTypeMismatchException.class})
    public ModelAndView handle(Exception ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }
}
