package com.augus.springbootinit.controller;


import cn.hutool.core.io.FileUtil;
import com.augus.springbootinit.annotation.AuthCheck;
import com.augus.springbootinit.bizmq.BiMessageProducer;
import com.augus.springbootinit.common.BaseResponse;
import com.augus.springbootinit.common.DeleteRequest;
import com.augus.springbootinit.common.ErrorCode;
import com.augus.springbootinit.common.ResultUtils;
import com.augus.springbootinit.constant.CommonConstant;
import com.augus.springbootinit.constant.UserConstant;
import com.augus.springbootinit.exception.BusinessException;
import com.augus.springbootinit.exception.ThrowUtils;
import com.augus.springbootinit.manager.AiManager;
import com.augus.springbootinit.manager.RedisLimiterManager;
import com.augus.springbootinit.model.dto.chart.*;
import com.augus.springbootinit.model.entity.Chart;
import com.augus.springbootinit.model.entity.User;
import com.augus.springbootinit.model.vo.BiResponse;
import com.augus.springbootinit.service.ChartService;
import com.augus.springbootinit.service.UserService;
import com.augus.springbootinit.utils.ExcelUtils;
import com.augus.springbootinit.utils.SqlUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;


@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;


    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */

    // todo 分页功能
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }
    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


    /**
     * 智能分析（异步）
     * todo 调用AI需要有系统预设，提前告诉他智能、功能、回复格式要求 + 分析目标 + 压缩后的数据
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    // todo genChartByAi开发
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        long biModelId = CommonConstant.BI_MODEL_ID;  // todo 如果不对，需要修改

        // todo 第一阶段 实现文件上传后的输出
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // todo 第四阶段 安全性校验
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1*1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // todo 第五阶段 限流
        // 每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + String.valueOf(loginUser.getId()));

        // todo excelTOCsv返回的是压缩后的数据
//        return ResultUtils.success(ExcelUtils.excelToCsv(multipartFile));

        // todo 第二阶段  加工喂给AI的数据
        // 因为使用yucongmingAi所以可以不用谢prompt
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师，我回给你数据，请你告诉我分析结论").append("\n");
//        userInput.append("分析目标是").append(goal).append("\n");
//        String result = ExcelUtils.excelToCsv(multipartFile);
//        userInput.append("数据如下：\n").append(result);
//        return ResultUtils.success(userInput.toString());
        userInput.append("分析需求：").append("\n");

        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal += ", 请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String dataToCsv = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(dataToCsv).append("\n");


        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setChartData(dataToCsv);
        chart.setChartType(chartType);
        chart.setStatus("wait");
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
        Long currentUserId = userService.getLoginUser(request).getId();
        chart.setUserId(currentUserId);
        chart.setCreateTime(new Date());
        chart.setUpdateTime(new Date());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图标保存失败");

        // todo 建议处理一下 try-catch
        CompletableFuture.runAsync(() -> {
            // 先修改图标任务状态为“执行中”。等执行成功后，修改为“已成功”、保存执行结果；执行失败后，状态修改为“失败”，记录任务失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if(!b){
//                throw new BusinessException(ErrorCode.OPERATION_ERROR, " 图表状态更新失败");
                handleChartUpdateError(chart.getId(),"图表状态更新失败");
                return;
            }

            // 调用AI
            String result = aiManager.doChat(biModelId, userInput.toString());
            String[] splits = result.split("【【【【【");
            if(splits.length < 3){
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
                handleChartUpdateError(chart.getId()," AI 生成错误");
                return;
            }
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();

            Chart updateChart2 = new Chart();
            updateChart2.setGenChart(genChart);
            updateChart2.setGenResult(genResult);
            updateChart2.setId(chart.getId());
            updateChart2.setStatus("succeed");
            boolean bb = chartService.updateById(updateChart2);
            if(!bb){
                handleChartUpdateError(chart.getId(), " 图表状态更新失败");
            }
        },threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setCharId(chart.getId());

        return ResultUtils.success(biResponse);

//        Chart chart = new Chart();
//        chart.setName(name);
//        chart.setGoal(userGoal);
//        chart.setChartData(dataToCsv);
//        chart.setChartType(chartType);
//        chart.setStatus("wait");
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
//        Long currentUserId = userService.getLoginUser(request).getId();
//        chart.setUserId(currentUserId);
//        chart.setCreateTime(new Date());
//        chart.setUpdateTime(new Date());
//        boolean saveResult = chartService.save(chart);
//        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图标保存失败");
//
//
//
//        BiResponse biResponse = new BiResponse();
//        biResponse.setGenChart(genChart);
//        biResponse.setGenResult(genResult);
//        biResponse.setCharId(chart.getId());
//
//        return ResultUtils.success(biResponse);
    }



    /**
     * 智能分析（同步）
     * todo 调用AI需要有系统预设，提前告诉他智能、功能、回复格式要求 + 分析目标 + 压缩后的数据
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    // todo genChartByAi开发
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        long biModelId = CommonConstant.BI_MODEL_ID;  // todo 如果不对，需要修改

        // todo 第一阶段 实现文件上传后的输出
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // todo 第四阶段 安全性校验
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1*1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // todo 第五阶段 限流
        // 每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + String.valueOf(loginUser.getId()));

        // todo excelTOCsv返回的是压缩后的数据
//        return ResultUtils.success(ExcelUtils.excelToCsv(multipartFile));

        // todo 第二阶段  加工喂给AI的数据
        // 因为使用yucongmingAi所以可以不用谢prompt
        StringBuilder userInput = new StringBuilder();
//        userInput.append("你是一个数据分析师，我回给你数据，请你告诉我分析结论").append("\n");
//        userInput.append("分析目标是").append(goal).append("\n");
//        String result = ExcelUtils.excelToCsv(multipartFile);
//        userInput.append("数据如下：\n").append(result);
//        return ResultUtils.success(userInput.toString());
        userInput.append("分析需求：").append("\n");

        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal += ", 请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String dataToCsv = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(dataToCsv).append("\n");

        String result = aiManager.doChat(biModelId, userInput.toString());
        String[] splits = result.split("【【【【【");
        if(splits.length < 3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(userGoal);
        chart.setChartData(dataToCsv);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        Long currentUserId = userService.getLoginUser(request).getId();
        chart.setUserId(currentUserId);
        chart.setCreateTime(new Date());
        chart.setUpdateTime(new Date());
        chart.setStatus("succeed");
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图标保存失败");


        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setCharId(chart.getId());

        return ResultUtils.success(biResponse);
    }

    private void handleChartUpdateError(long chartId, String execMessage){
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if(!updateResult){
//            throw new BusinessException(ErrorCode.OPERATION_ERROR, " 图表状态更新失败");
            log.info(chartId + "失败： " + execMessage);
        }
    }



    /**
     * 智能分析（异步消息队列）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                        GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        // 校验文件大小
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过 1M");
        // 校验文件后缀 aaa.png
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        // 无需写 prompt，直接调用现有模型，https://www.yucongming.com，公众号搜【鱼聪明AI】
//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
//                "分析需求：\n" +
//                "{数据分析的需求或者目标}\n" +
//                "原始数据：\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
//        long biModelId = 1659171950288818178L;
        // 分析需求：
        // 分析网站用户的增长情况
        // 原始数据：
        // 日期,用户数
        // 1号,10
        // 2号,20
        // 3号,30

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);// todo 先不执行业务，先将其保存起来，等待执行
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        long newChartId = chart.getId();


        // todo 直接放入消息队列执行
        biMessageProducer.sendMessage(String.valueOf(newChartId));

        BiResponse biResponse = new BiResponse();
//        biResponse.setChartId(newChartId);
        biResponse.setCharId(newChartId);
        return ResultUtils.success(biResponse);
    }



}
