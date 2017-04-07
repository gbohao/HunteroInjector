package com.huntero.injector.compiler;

import java.util.List;

/**
 * Created by huntero on 17-4-6.
 */

public class MethodPermission {
    private String name;
    private List<Parameter> parameters;

    public MethodPermission(String name, List<Parameter> parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }
}
