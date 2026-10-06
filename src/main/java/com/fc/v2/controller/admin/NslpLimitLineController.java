package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.service.ITNslpLimitLineService;
import com.fc.v2.util.DateUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 功能区昼夜间限值线 Controller（rule-eval 形状：只有回话，没有增删改）。
 * 按线代号取值与整表取优两路、大屏昼段夜段两截折线，都只调本处同一批算法。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Api(value = "功能区昼夜间限值线判层")
@Controller
@RequestMapping("/nslpLimitLine")
public class NslpLimitLineController extends BaseController {

    @Autowired
    private ITNslpLimitLineService nslpLimitLineService;

    @Log(title = "限值线按线代号取值", action = "resolve")
    @ApiOperation(value = "按线代号取值", notes = "按 ruleCode 在 at 时刻在位的版本判层")
    @GetMapping("/resolve")
    @RequiresPermissions("nslp:limitLine:eval")
    @ResponseBody
    public AjaxResult resolve(String ruleCode, BigDecimal input, String at) {
        LimitLineVerdict v = nslpLimitLineService.resolve(ruleCode, input, DateUtils.parseDate(at));
        return AjaxResult.successData(200, v);
    }

    @Log(title = "限值线整表取优", action = "resolveTop")
    @ApiOperation(value = "整表取优", notes = "at 时刻在位线中交班次序最高者开口")
    @GetMapping("/resolveTop")
    @RequiresPermissions("nslp:limitLine:eval")
    @ResponseBody
    public AjaxResult resolveTop(BigDecimal input, String at) {
        LimitLineVerdict v = nslpLimitLineService.resolveTop(input, DateUtils.parseDate(at));
        return AjaxResult.successData(200, v);
    }

    @Log(title = "限值线大屏批量回算", action = "resolveBatch")
    @ApiOperation(value = "批量回算", notes = "大屏昼段夜段折线/点值/后端逐条回算同一次计算")
    @PostMapping("/resolveBatch")
    @RequiresPermissions("nslp:limitLine:eval")
    @ResponseBody
    public AjaxResult resolveBatch(@RequestBody List<LevelQuery> queries) {
        // 问话里 at 反序列化走 @JsonFormat；库只照一面，回话只算一遍
        List<LimitLineVerdict> verdicts = nslpLimitLineService.resolveBatch(queries);
        return AjaxResult.successData(200, verdicts);
    }

    @Log(title = "限值线现行清单", action = "available")
    @ApiOperation(value = "现行清单", notes = "at 时刻在位、配置干净的线（撤下线不进）")
    @GetMapping("/available")
    @RequiresPermissions("nslp:limitLine:view")
    @ResponseBody
    public AjaxResult available(String at) {
        Date moment = DateUtils.parseDate(at);
        return AjaxResult.successData(200, nslpLimitLineService.listAvailable(moment));
    }

    @Log(title = "区册季度取线", action = "zoneQuarter")
    @ApiOperation(value = "按区册按季度取线", notes = "补算往年点位用当时在位版本，不拿新版套旧账")
    @GetMapping("/zoneQuarter")
    @RequiresPermissions("nslp:limitLine:view")
    @ResponseBody
    public AjaxResult zoneQuarter(String zoneNo, Integer year, Integer quarter) {
        return AjaxResult.successData(200,
                nslpLimitLineService.listByZoneQuarter(zoneNo,
                        year == null ? 0 : year, quarter == null ? 0 : quarter));
    }
}
