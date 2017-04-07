package com.huntero.injector.compiler;

import com.squareup.javapoet.TypeName;

/**
 * Created by huntero on 17-4-6.
 */

final class Parameter {
    static final Parameter[] NONE = new Parameter[0];
    private int position;
    private TypeName type;

    Parameter(int position, TypeName type) {
        this.position = position;
        this.type = type;
    }

    int getPosition() {
        return position;
    }

    TypeName getType() {
        return type;
    }
}
