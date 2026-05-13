/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.star.beans.XPropertySet
 *  com.sun.star.lib.uno.typeinfo.MethodTypeInfo
 *  com.sun.star.lib.uno.typeinfo.TypeInfo
 *  com.sun.star.uno.XInterface
 */
package org.libreoffice.rest;

import com.sun.star.beans.XPropertySet;
import com.sun.star.lib.uno.typeinfo.MethodTypeInfo;
import com.sun.star.lib.uno.typeinfo.TypeInfo;
import com.sun.star.uno.XInterface;

public interface XLibreOfficeRest
extends XInterface {
    public static final TypeInfo[] UNOTYPEINFO = new TypeInfo[]{new MethodTypeInfo("PING", 0, 0), new MethodTypeInfo("RESTINFO", 1, 0), new MethodTypeInfo("JAVAVERSION", 2, 0), new MethodTypeInfo("LASTERROR", 3, 0), new MethodTypeInfo("HTTPGET", 4, 0), new MethodTypeInfo("HTTPGETREFRESH", 5, 0), new MethodTypeInfo("HTTPGETDEBUG", 6, 0), new MethodTypeInfo("HTTPGETHEADER", 7, 0), new MethodTypeInfo("HTTPGETHEADERS", 8, 0), new MethodTypeInfo("HTTPPOST", 9, 0), new MethodTypeInfo("HTTPPOSTHEADER", 10, 0), new MethodTypeInfo("JSONPATH", 11, 0), new MethodTypeInfo("JSONNUMBER", 12, 0), new MethodTypeInfo("JSONBOOL", 13, 0), new MethodTypeInfo("JSONVALID", 14, 0), new MethodTypeInfo("URLENCODE", 15, 0)};

    public String PING(XPropertySet var1);

    public String RESTINFO(XPropertySet var1);

    public String JAVAVERSION(XPropertySet var1);

    public String LASTERROR(XPropertySet var1);

    public String HTTPGET(XPropertySet var1, String var2);

    public String HTTPGETREFRESH(XPropertySet var1, String var2, Object var3);

    public String HTTPGETDEBUG(XPropertySet var1, String var2);

    public String HTTPGETHEADER(XPropertySet var1, String var2, String var3, String var4);

    public String HTTPGETHEADERS(XPropertySet var1, String var2, String var3);

    public String HTTPPOST(XPropertySet var1, String var2, String var3, String var4);

    public String HTTPPOSTHEADER(XPropertySet var1, String var2, String var3, String var4, String var5, String var6);

    public String JSONPATH(XPropertySet var1, String var2, String var3);

    public double JSONNUMBER(XPropertySet var1, String var2, String var3);

    public double JSONBOOL(XPropertySet var1, String var2, String var3);

    public double JSONVALID(XPropertySet var1, String var2);

    public String URLENCODE(XPropertySet var1, String var2);
}
