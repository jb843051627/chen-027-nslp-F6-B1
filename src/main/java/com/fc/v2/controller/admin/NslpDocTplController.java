package com.fc.v2.controller.admin;

import java.util.Date;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpDocTpl;
import com.fc.v2.model.custom.DocIssueSubmit;
import com.fc.v2.service.ITNslpDocTplService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.DateUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 执法文书版次与出文 Controller。
 *
 * 取值只认版次、且只由服务端一处给出：打印环节照这一纸回话，页面不许再算第二遍。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "执法文书版次与出文")
@Controller
@RequestMapping("/NslpDocTplController")
public class NslpDocTplController {

    @Autowired
    private ITNslpDocTplService nslpDocTplService;

    @ApiOperation(value = "照主键查版", notes = "撤版的版也查得着")
    @GetMapping("/get")
    @RequiresPermissions("nslp:nslpPoint:view")
    @ResponseBody
    public AjaxResult get(Long id) {
        return AjaxResult.successData(200, nslpDocTplService.selectById(id));
    }

    @ApiOperation(value = "现行可选版清单", notes = "出文时刻在位、干净、按版次降序；撤版不进")
    @GetMapping("/current")
    @RequiresPermissions("nslp:nslpPoint:view")
    @ResponseBody
    public AjaxResult current(String tplName, String at) {
        Date moment = DateUtils.parseDate(at);
        return AjaxResult.successData(200,
                nslpDocTplService.listCurrent(tplName, moment == null ? new Date() : moment));
    }

    @ApiOperation(value = "开口版（唯一取值口）", notes = "按版次取值与整表取优同走此处，两答同一版")
    @GetMapping("/resolve")
    @RequiresPermissions("nslp:nslpPoint:view")
    @ResponseBody
    public AjaxResult resolve(String tplName, String at) {
        Date moment = DateUtils.parseDate(at);
        return AjaxResult.successData(200,
                nslpDocTplService.resolveVersion(tplName, moment == null ? new Date() : moment));
    }

    @Log(title = "立一版（miss 服务端轧）", action = "saveVersion")
    @ApiOperation(value = "立版/改版", notes = "miss_num=var-fill 服务端写回；不接手填")
    @PostMapping("/save")
    @RequiresPermissions("nslp:nslpPoint:add")
    @ResponseBody
    public AjaxResult save(@RequestBody TNslpDocTpl tpl) {
        return AjaxResult.successData(200,
                nslpDocTplService.saveVersion(tpl, ShiroUtils.getLoginName()));
    }

    @Log(title = "撤版", action = "withdrawVersion")
    @ApiOperation(value = "撤版", notes = "记已撤版不进现行清单；旧文档不回改")
    @PostMapping("/withdraw")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult withdraw(Long id) {
        return AjaxResult.successData(200,
                nslpDocTplService.withdrawVersion(id, ShiroUtils.getLoginName()));
    }

    @Log(title = "出文（按当版取值）", action = "issue")
    @ApiOperation(value = "出文", notes = "照出文时刻选版；缺项逐项点名，齐了才发并钉版留档")
    @PostMapping("/issue")
    @RequiresPermissions("nslp:nslpPoint:add")
    @ResponseBody
    public AjaxResult issue(@RequestBody DocIssueSubmit submit) {
        if (submit.getOperator() == null || submit.getOperator().trim().isEmpty()) {
            submit.setOperator(ShiroUtils.getLoginName());
        }
        return AjaxResult.successData(200, nslpDocTplService.issue(submit));
    }
}
