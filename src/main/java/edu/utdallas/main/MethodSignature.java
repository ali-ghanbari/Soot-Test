package edu.utdallas.main;

import soot.SootMethod;

public class MethodSignature {
    private final String declaringClassName;
    private final String methodName;
    private final String parameterTypes;
    
    public MethodSignature(final SootMethod method) {
        this.declaringClassName = method.getDeclaringClass().getName();
        this.methodName = method.getName();
        final String temp = method.getParameterTypes().toString();
        this.parameterTypes = temp.substring(1, temp.length() - 1);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((declaringClassName == null) ? 0 : declaringClassName.hashCode());
        result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
        result = prime * result + ((parameterTypes == null) ? 0 : parameterTypes.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MethodSignature other = (MethodSignature) obj;
        if (declaringClassName == null) {
            if (other.declaringClassName != null)
                return false;
        } else if (!declaringClassName.equals(other.declaringClassName))
            return false;
        if (methodName == null) {
            if (other.methodName != null)
                return false;
        } else if (!methodName.equals(other.methodName))
            return false;
        if (parameterTypes == null) {
            if (other.parameterTypes != null)
                return false;
        } else if (!parameterTypes.equals(other.parameterTypes))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("%s::%s(%s)", this.declaringClassName, this.methodName, this.parameterTypes);
    }
}
