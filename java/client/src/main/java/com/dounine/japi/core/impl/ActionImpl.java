package com.dounine.japi.core.impl;

import com.alibaba.fastjson.JSON;
import com.dounine.japi.common.Const;
import com.dounine.japi.core.*;
import com.dounine.japi.core.annotation.IActionRequest;
import com.dounine.japi.core.annotation.impl.ActionRequest;
import com.dounine.japi.core.annotation.impl.ActionRequestImpl;
import com.dounine.japi.core.type.DocType;
import com.dounine.japi.core.type.RequestMethod;
import com.dounine.japi.entity.User;
import com.dounine.japi.exception.JapiException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by huanghuanlai on 2017/1/18.
 */
public class ActionImpl implements IAction {

    private String projectPath;
    private String javaFilePath;
    private List<String> includePaths = new ArrayList<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionImpl.class);

    @Override
    public String readClassInfo() {
        return null;
    }

    public String readClassInfo(@Validated(value = {}) User user) {
        return null;
    }

    @Override
    public String readPackageName() {
        return null;
    }

    @Override
    public List<String> getExcludeTypes() {
        URL url = this.getClass().getResource("/action-builtIn-types.txt");
        File file = new File(url.getFile());
        if (!file.exists()) {
            throw new JapiException(url.getFile() + " 文件不存在");
        }
        try {
            String str = FileUtils.readFileToString(file, Charset.forName("utf-8"));
            str = str.replaceAll("\\s", StringUtils.EMPTY);
            return Arrays.asList(str.split(","));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    @Override
    public List<IActionMethod> getMethods() {
        List<IActionMethod> methods = null;
        try {
            List<String> javaFileLines = FileUtils.readLines(new File(javaFilePath), Charset.forName("utf-8"));
            List<String> noPackageLines = new ArrayList<>();
            boolean match = false;//true 找到类的开始，开始查找方法
            for (String line : javaFileLines) {
                if (!match) {
                    for (String chart : Const.MATCH_CHARTS) {
                        if (line.startsWith(chart)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) {
                    noPackageLines.add(line);
                }
            }
            noPackageLines = noPackageLines.subList(1, noPackageLines.size() - 1);//去掉类头与尾巴
            List<List<String>> methodBodyAndDocs = methodBodyAndDoc(noPackageLines);
            methods = extractDocAndMethodInfo(methodBodyAndDocs);//提取方法注释及方法信息

        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new JapiException(e.getMessage());
        }
        return methods;
    }

    /**
     * 提供类的方法信息
     *
     * @param noPackageLines 不包含package头部与类尾部行信息
     * @return 方法信息列表
     */
    private List<List<String>> methodBodyAndDoc(final List<String> noPackageLines) {
        List<List<String>> methodBodyAndDocs = new ArrayList<>();
        boolean isFindDocBegin = false;
        List<String> methodLines = null;
        Iterator<String> newNoPackageLines = new ArrayList<>(noPackageLines).iterator();
        while (newNoPackageLines.hasNext()) {
            String line = newNoPackageLines.next();
            Matcher docMatcher = Const.DOC_PATTERN_BEGIN.matcher(line);
            if (!isFindDocBegin && docMatcher.find()) {//匹配到注释
                isFindDocBegin = true;
            }
            if (isFindDocBegin) {
                if (null == methodLines) {
                    methodLines = new ArrayList<>();
                    methodLines.add(line);
                    newNoPackageLines.remove();
                } else if (null != methodLines && methodLines.size() > 0) {
                    methodLines.add(line);
                    newNoPackageLines.remove();
                }
            }
            if (null != methodLines && methodLines.size() > 0) {
                for (Pattern methodPattern : Const.METHOD_KEYWORD) {
                    Matcher matcher = methodPattern.matcher(line);
                    if (matcher.find()) {//匹配到方法
                        methodBodyAndDocs.add(methodLines);
                        methodLines = null;
                        isFindDocBegin = false;
                        break;
                    }
                }
            }
        }

        return methodBodyAndDocs;
    }

    /**
     * 提取方法注释信息
     *
     * @param methodLines
     * @return
     */
    private List<IActionMethodDoc> extractDoc(final List<String> methodLines) {
        boolean methodBegin = false;
        List<IActionMethodDoc> methodDocs = new ArrayList<>();

        for (String methodLine : methodLines) {
            Matcher matcherDocBegin = Const.DOC_PATTERN_BEGIN.matcher(methodLine);
            if (!methodBegin && matcherDocBegin.find()) {
                methodBegin = true;
                continue;
            }
            if (methodBegin) {
                Matcher matcherDocEnd = Const.DOC_PATTERN_END.matcher(methodLine);
                if (matcherDocEnd.find()) {
                    break;
                }
            }
            if (methodBegin) {
                ActionMethodDocImpl docImpl = new ActionMethodDocImpl();
                Matcher methodFunDesMatcher = Const.DOC_METHOD_FUN_DES.matcher(methodLine);//方法功能描述
                if (methodFunDesMatcher.find()) {
                    Matcher methodMoreMatcher = Const.DOC_MORE.matcher(methodLine);
                    if (methodMoreMatcher.find()) {
                        docImpl.setName(methodFunDesMatcher.group().substring(methodMoreMatcher.group().length()));
                        docImpl.setDocType(DocType.FUNDES.name());
                    }
                } else {
                    Matcher methodMoreMatcher = Const.DOC_MORE.matcher(methodLine);//注释左   *
                    if (methodMoreMatcher.find()) {//   *
                        docImpl.setName(methodLine.substring(methodMoreMatcher.group().length()));
                        Matcher methodNameMatcher = Const.DOC_NAME.matcher(methodLine);//注释名称 * \@param
                        if (methodNameMatcher.find()) {
                            String methodNameValue = methodNameMatcher.group();
                            String docName = methodNameValue.substring(3);
                            docImpl.setName(docName);
                            Matcher methodNameValueMatcher = Const.DOC_NAME_VALUE.matcher(methodLine);//注释名称 * \@param user
                            String docTagDes = DocTagImpl.getInstance().getTagDesByName(docName);
                            String _docTagDes = StringUtils.isBlank(docTagDes) ? DocTagImpl.getInstance().getTagDesByName(docName + ".") : docTagDes;
                            boolean isSingleTag = StringUtils.isBlank(docTagDes) && !(StringUtils.isNotBlank(_docTagDes) && _docTagDes.equals(docTagDes));
                            if (StringUtils.isNotBlank(_docTagDes)) {//是否匹配注释tag：return.
                                docImpl.setDocType(_docTagDes);
                                String valueAndDes = methodLine.substring(methodLine.indexOf(docName) + docName.length()).trim();
                                if (!isSingleTag) {
                                    int emptySpaceIndex = valueAndDes.indexOf(StringUtils.SPACE);
                                    if (emptySpaceIndex == -1) {
                                        docImpl.setValue(valueAndDes);
                                        LOGGER.warn(methodLine.trim().substring(2) + " 没有注释信息.");
                                    } else {
                                        docImpl.setValue(StringUtils.substring(valueAndDes,0,emptySpaceIndex));
                                        docImpl.setDes(StringUtils.substring(valueAndDes,docImpl.getValue().length()).trim());
                                    }
                                } else {
                                    docImpl.setValue(valueAndDes);
                                }

                            } else if (methodNameValueMatcher.find()) {
                                String docValueAndDes = methodNameValueMatcher.group();
                                String docValue = docValueAndDes.substring(methodNameValue.length());
                                if (methodLine.endsWith(docValue)) {
                                    String[] docTypeAndValue = docValueAndDes.substring(3).split(StringUtils.SPACE);
                                    docImpl.setDocType(docTypeAndValue[0]);
                                    docImpl.setValue(docTypeAndValue[1]);
                                } else {
                                    String docDes = methodLine.substring(methodLine.indexOf(docValue)).trim().substring(docValue.length());
                                    docImpl.setDes(docDes.trim());
                                    docImpl.setValue(docValue);
                                }
                            }
                        }
                    }
                }
                if (StringUtils.isBlank(docImpl.getName()) && StringUtils.isBlank(docImpl.getValue()) && StringUtils.isBlank(docImpl.getDes())) {
                    continue;//去掉无效的换行注释
                }
                methodDocs.add(docImpl);
            }
        }
        return methodDocs;
    }


    private IReturnType getMethodReturnType(final String returnTypeStr) {
        ReturnTypeImpl returnTypeImpl = new ReturnTypeImpl();
        returnTypeImpl.setJavaFilePath(javaFilePath);
        returnTypeImpl.setProjectPath(projectPath);
        returnTypeImpl.setIncludePaths(includePaths);
        returnTypeImpl.setJavaKeyTxt(returnTypeStr);
        return returnTypeImpl;
    }

    /**
     * 获取方法返回值
     *
     * @param methodLineStr 方法行
     * @return 类型：String
     */
    private String getMethodReturnTypeStr(final String methodLineStr) {
        String returnTypeStr = null;
        for (Pattern typePattern : Const.METHOD_RETURN_TYPES) {
            Matcher returnTypeMatch = typePattern.matcher(methodLineStr);
            if (returnTypeMatch.find()) {
                returnTypeStr = StringUtils.substring(returnTypeMatch.group(), 0, -1).trim();//public String testUser
                break;
            }
        }
        String returnType = null;
        if (StringUtils.isNotBlank(returnTypeStr)) {
            returnType = returnTypeStr.substring(returnTypeStr.indexOf(StringUtils.SPACE), returnTypeStr.lastIndexOf(StringUtils.SPACE));
            returnType = returnType.trim();
        }
        return returnType;
    }

    /**
     * 获取方法参数信息
     *
     * @param methodLineStr 方法行
     * @return 参数列表
     */
    private List<String> getMethodParameters(String methodLineStr) {
        List<String> parameters = new ArrayList<>();
        Matcher matcher = Const.PARAMETER_BODYS.matcher(methodLineStr);
        if (matcher.find()) {
            String parStrs = StringUtils.substring(matcher.group(), 1, -1).trim();
            parStrs = StringUtils.substring(parStrs.trim(), 0, -1);
            Matcher parMatcher = Const.PARAMETER_SINGLE_NAME.matcher(parStrs);
            int initSearchIndex = 0;
            int lastSearchIndex = 0;
            while (parMatcher.find()) {
                lastSearchIndex = parMatcher.end();
                parameters.add(parStrs.substring(initSearchIndex, lastSearchIndex - 1).trim());
                initSearchIndex = lastSearchIndex;
            }
            if (lastSearchIndex < parStrs.length()) {
                parameters.add(parStrs.substring(lastSearchIndex));
            }
        }
        return parameters;
    }

    /**
     * 提取方法信息
     *
     * @param methodLines 方法doc及注解及行信息
     * @return 方法信息
     */
    private MethodImpl extractMethod(List<String> methodLines) {
        MethodImpl methodImpl = new MethodImpl();
        List<String> annotationStrs = new ArrayList<>();
        String methodLineStr = null;
        boolean docBegin = false;
        for (String methodLine : methodLines) {
            Matcher matcherDocEnd = Const.DOC_PATTERN_END.matcher(methodLine);
            if (!docBegin && matcherDocEnd.find()) {
                docBegin = true;
                continue;
            }
            if (docBegin) {
                Matcher annotationMatcher = Const.ANNOTATION_PATTERN.matcher(methodLine);
                if (annotationMatcher.find()) {//注解
                    annotationStrs.add(methodLine.trim().substring(1));
                } else {//方法
                    methodLineStr = methodLine.trim();
                }
            }
        }
        String returnTypeStr = getMethodReturnTypeStr(methodLineStr);
        IReturnType returnType = getMethodReturnType(returnTypeStr);
        ActionRequest actionRequest = getRequestsByAnnotations(annotationStrs);

        List<String> methodParameters = getMethodParameters(methodLineStr);

        methodImpl.setReturnType(returnType);
        methodImpl.setAnnotations(annotationStrs);
        methodImpl.setRequest(actionRequest);
        methodImpl.setParameters(methodParameters);

        return methodImpl;
    }

    /**
     * 从注解中获取请求url地扯
     *
     * @param annotationStrs
     * @return
     */
    private ActionRequest getRequestsByAnnotations(List<String> annotationStrs) {
        List<IActionRequest> actionRequests = new ArrayList<>();
        actionRequests.add(new ActionRequestImpl(RequestMethod.GET,"org.springframework.web.bind.annotation.GetMapping", true, "value"));
        actionRequests.add(new ActionRequestImpl(RequestMethod.POST,"org.springframework.web.bind.annotation.PostMapping", true, "value"));
        actionRequests.add(new ActionRequestImpl(RequestMethod.PUT,"org.springframework.web.bind.annotation.PutMapping", true, "value"));
        actionRequests.add(new ActionRequestImpl(RequestMethod.DELETE,"org.springframework.web.bind.annotation.DeleteMapping", true, "value"));
        actionRequests.add(new ActionRequestImpl(RequestMethod.PATCH,"org.springframework.web.bind.annotation.PatchMapping", true, "value"));

        LOGGER.info("====");
        String requestAnno = null, requestAnnoOrign = null;
        Pattern REQUEST_ANNO_PATTERN = Pattern.compile("^\\S+[(]");
        for (String annotationLine : annotationStrs) {
            Matcher requestAnnoMatcher = REQUEST_ANNO_PATTERN.matcher(annotationLine);
            if (requestAnnoMatcher.find()) {
                requestAnnoOrign = annotationLine;
                requestAnno = StringUtils.substring(requestAnnoMatcher.group(), 0, -1);
                break;
            }
        }

        IActionRequest actionRequest = null;
        if (StringUtils.isNotBlank(requestAnno)) {
            for (IActionRequest ar : actionRequests) {
                if (ar.annotation().equals(requestAnno)) {
                    actionRequest = ar;
                    break;
                } else if (ar.annotation().indexOf(".") >= -1) {
                    if (ar.annotation().endsWith(requestAnno)) {
                        actionRequest = ar;
                        break;
                    }
                }
            }
        }
        String[] requestUrls = null;
        if (null != actionRequest) {
            Pattern pattern = Pattern.compile(actionRequest.valueField() + "(\\s){0,}[=](\\s){0,}");
            Matcher matcher = pattern.matcher(requestAnnoOrign);
            if (matcher.find()) {
                String arryOrSingle = matcher.group();
                String beginStr = StringUtils.substring(requestAnnoOrign, matcher.start());
                if (beginStr.startsWith(arryOrSingle + "\"")) {//单个值
                    String valueAndEndSym = beginStr.substring(arryOrSingle.length());
                    requestUrls = new String[]{StringUtils.substring(valueAndEndSym, 0, valueAndEndSym.lastIndexOf(")"))};
                } else if (beginStr.startsWith(arryOrSingle + "{")) {//多个值
                    Matcher symBeginMatcher = Const.PATTERN_SYM_BEGIN.matcher(beginStr);
                    Matcher symEndMatcher = Const.PATTERN_SYM_END.matcher(beginStr);
                    if (symBeginMatcher.find() && symEndMatcher.find()) {
                        String arrStr = StringUtils.substring(beginStr, symBeginMatcher.start() + 1, symEndMatcher.end() - 1).trim();
                        requestUrls = arrStr.split(",");
                    }
                }
            } else {
                String symAndValue = StringUtils.substring(requestAnnoOrign, requestAnno.length());
                requestUrls = new String[]{StringUtils.substring(symAndValue.trim(), 1, -1)};
            }
        }

        return new ActionRequest(requestUrls,actionRequest.getMethod());
    }

    /**
     * 提取方法信息
     *
     * @return 方法列表
     */
    private List<IActionMethod> extractDocAndMethodInfo(final List<List<String>> methodBodyAndDocs) {
        List<IActionMethod> methodImpls = new ArrayList<>(methodBodyAndDocs.size());
        for (List<String> methodLines : methodBodyAndDocs) {
            MethodImpl methodImpl = new MethodImpl();

            List<IActionMethodDoc> methodDocs = extractDoc(methodLines);//提取方法注释信息
            MethodImpl extractMethod = extractMethod(methodLines);//提取方法信息
            Iterator<IActionMethodDoc> methodDocIterator = methodDocs.iterator();
            while (methodDocIterator.hasNext()) {//提取方法描述信息
                IActionMethodDoc actionMethodDoc = methodDocIterator.next();
                if (DocType.FUNDES.name().equals(actionMethodDoc.getDocType())) {
                    methodImpl.setMethodDescription(actionMethodDoc.getName());
                    methodDocIterator.remove();
                    break;
                }
            }
            methodImpl.setDocs(methodDocs);
            methodImpl.setAnnotations(extractMethod.getAnnotations());
            methodImpl.setReturnType(extractMethod.getReturnType());
            methodImpl.setRequest(extractMethod.getRequest());
            methodImpl.setParameters(extractMethod.getParameters());

            methodImpls.add(methodImpl);
        }
        if (true) {
            for (IActionMethod actionMethod : methodImpls) {
                System.out.println("方法描述：" + actionMethod.getMethodDescription());
                System.out.println("请求信息：" + JSON.toJSON(actionMethod.getRequest()));
                System.out.println("参数类型：" + JSON.toJSONString(actionMethod.getParameters()));
                System.out.println("参数注解：" + JSON.toJSONString(actionMethod.getAnnotations()));
                boolean hasReturnDoc = false;
                for (IActionMethodDoc doc : actionMethod.getDocs()) {
                    if(doc.getName().equals("return")){
                        hasReturnDoc = true;
                    }
                    if(hasReturnDoc&&doc.getValue().split(" ")[0].equals("class")){
                        IReturnType returnType  = getMethodReturnType(doc.getValue().split(" ")[1]);
                        System.out.println(doc.getDocType()+"："+JSON.toJSONString(returnType));
                    }else{
                        System.out.println(doc.getDocType() + " : " + doc.getValue() + " " + doc.getDes());
                    }
                }
                if(!hasReturnDoc){
                    System.out.println(DocTagImpl.getInstance().getTagDesByName("return.")+"：" + JSON.toJSONString(actionMethod.getReturnType()));
                }
                System.out.println("----------");
            }
        }

        return methodImpls;
    }

    public String getJavaFilePath() {
        return javaFilePath;
    }

    public void setJavaFilePath(String javaFilePath) {
        this.javaFilePath = javaFilePath;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }
}
