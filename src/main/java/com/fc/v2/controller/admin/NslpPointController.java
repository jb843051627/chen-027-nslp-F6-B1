package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.util.DateUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 声环境监测点位底册 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "声环境监测点位底册")
@Controller
@RequestMapping("/NslpPointController")
public class NslpPointController extends BaseController {

    private final String prefix = "admin/nslpPoint";

    @Autowired
    private ITNslpPointService nslpPointService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("nslp:nslpPoint:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "声环境监测点位底册集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public ResultTable list(TNslpPoint record) {
        QueryWrapper<TNslpPoint> queryWrapper = new QueryWrapper<TNslpPoint>();
        startPage();
        com.github.pagehelper.PageInfo<TNslpPoint> page =
                new com.github.pagehelper.PageInfo<TNslpPoint>(nslpPointService.selectTNslpPointList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "声环境监测点位底册新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("nslp:nslpPoint:add")
    @ResponseBody
    public AjaxResult add(TNslpPoint record) {
        return toAjax(nslpPointService.insertTNslpPoint(record));
    }

    @Log(title = "声环境监测点位底册修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult editSave(TNslpPoint record) {
        return toAjax(nslpPointService.updateTNslpPoint(record));
    }

    @Log(title = "声环境监测点位底册删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("nslp:nslpPoint:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(nslpPointService.deleteTNslpPointByIds(ids));
    }

    @Log(title = "点位归层并钉线", action = "pinLevel")
    @ApiOperation(value = "点位归层", notes = "走限值线唯一算法；归层钉线代号与起用日，不归层两格清空白")
    @GetMapping("/pinLevel")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult pinLevel(Long id, BigDecimal input, String at, String objKind) {
        LimitLineVerdict v = nslpPointService.pinPointLevel(id, input, DateUtils.parseDate(at), objKind);
        return AjaxResult.successData(200, v);
    }

    @Log(title = "点位批量归层（大屏同次计算）", action = "pinLevelBatch")
    @ApiOperation(value = "批量归层", notes = "大屏昼段夜段折线/点值/后端回算同一次计算并逐行钉线")
    @PostMapping("/pinLevelBatch")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult pinLevelBatch(@RequestBody List<LevelQuery> queries) {
        return AjaxResult.successData(200, nslpPointService.pinPointLevelBatch(queries));
    }

    @Log(title = "看板挂码孤儿点位", action = "pinnedOrphans")
    @ApiOperation(value = "挂码孤儿", notes = "挂着线代号、回到 at 那一刻却寻不到在位版本的点位一条条列出")
    @GetMapping("/pinnedOrphans")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public AjaxResult pinnedOrphans(String at) {
        return AjaxResult.successData(200, nslpPointService.listPinnedOrphans(DateUtils.parseDate(at)));
    }
}
