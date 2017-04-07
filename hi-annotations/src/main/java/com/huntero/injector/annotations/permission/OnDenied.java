package com.huntero.injector.annotations.permission;

import com.huntero.injector.annotations.permission.internal.PermissionAnnotationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by huntero on 17-3-27.
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@PermissionAnnotationType(field = "methodForDenied")
public @interface OnDenied {
    String[] value();
}
