package com.huntero.injector.compiler;

/**
 * Created by huntero on 17-3-30.
 */

public class PermissionBinding {
    static final int TYPE_APPLY = 0x01;
    static final int TYPE_DENIED = 0x03;
    static final int TYPE_NEVERASKAGAIN = 0x04;
    static final int TYPE_SHOWRATIONALE = 0x02;

    private MethodPermission methodForApply;
    private MethodPermission methodForDenied;
    private MethodPermission methodForNeverAskAgain;
    private MethodPermission methodForShowRationale;
    private String[] permissions;

    public void setMethodForApply(MethodPermission name) {
        this.methodForApply = name;
    }

    public MethodPermission getMethodForApply() {
        return methodForApply;
    }

    public MethodPermission getMethodForDenied() {
        return methodForDenied;
    }

    public void setMethodForDenied(MethodPermission methodForDenied) {
        this.methodForDenied = methodForDenied;
    }

    public MethodPermission getMethodForNeverAskAgain() {
        return methodForNeverAskAgain;
    }

    public void setMethodForNeverAskAgain(MethodPermission methodForNeverAskAgain) {
        this.methodForNeverAskAgain = methodForNeverAskAgain;
    }

    public MethodPermission getMethodForShowRationale() {
        return methodForShowRationale;
    }

    public void setMethodForShowRationale(MethodPermission methodForShowRationale) {
        this.methodForShowRationale = methodForShowRationale;
    }

    public void setPermissions(String[] permissions) {
        this.permissions = permissions;
    }

    public String[] getPermissions() {
        return permissions;
    }
}
