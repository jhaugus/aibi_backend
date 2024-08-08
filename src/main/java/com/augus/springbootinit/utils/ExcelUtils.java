package com.augus.springbootinit.utils;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel 相关工具类
 */
@Slf4j
public class ExcelUtils {

    /**
     * excel 转 csv
     *
     * @param multipartFile
     * @return
     */
    public static String excelToCsv(MultipartFile multipartFile){
        // todo 第一阶段 喂入文件进行测试
//        File file = null;
//        try {
//            file = ResourceUtils.getFile("classpath:网站数据.xlsx");
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//        List<Map<Integer, String>> list = EasyExcel.read(file)
//                .excelType(ExcelTypeEnum.XLSX)
//                .sheet()
//                .headRowNumber(0)
//                .doReadSync();
        // todo 第二阶段  读取数据，在controller中使用
        List<Map<Integer, String>> list = null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (IOException e) {
            log.error("表格处理错误", e);
        }

        if(CollUtil.isEmpty(list)){
            return "";
        }


        StringBuilder stringBuilder = new StringBuilder();
        // TODO 过滤为null的数据
        LinkedHashMap<Integer, String> headMap = (LinkedHashMap) list.get(0);
        List<String> headerList = headMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
//        System.out.println(StringUtils.join(headerList, ","));
        stringBuilder.append(StringUtils.join(headerList, ","));
        // 读取数据
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap) list.get(i);
            List<String> datarList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
//            System.out.println(StringUtils.join(datarList, ","));
//            System.out.println(StringUtils.join(headMap.values(), ","));
            stringBuilder.append(StringUtils.join(datarList, ",")).append("\n");
        }
//        System.out.println(list);
        return stringBuilder.toString();
    }


}
