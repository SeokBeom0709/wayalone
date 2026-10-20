package com.example.demo.member;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 로그인한 회원을 주입합니다.
 * 기본은 로그인하지 않았으면 401 이고, required=false 이면 로그인하지 않았을 때 null 을 줍니다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginMember {

    boolean required() default true;
}
