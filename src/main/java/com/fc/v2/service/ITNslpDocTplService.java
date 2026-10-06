package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpDocTpl;
import com.fc.v2.model.custom.DocIssueResult;
import com.fc.v2.model.custom.DocIssueSubmit;

/**
 * 执法文书版次与出文 Service接口。
 *
 * 取值只由服务端这一处给出：选版窗口、还差几项的轧法、缺项逐项点名、出文留档，
 * 别处不得再算第二遍。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITNslpDocTplService {

    /** 照主键查（已撤版的版查得着，只是不进现行可选清单） */
    TNslpDocTpl selectById(Long id);

    /**
     * 现行可选清单：该文种在 at 那一刻在位（情形 0、在效期窗内）且配置干净的版，
     * 按版次从大到小排。已撤版不进。
     */
    List<TNslpDocTpl> listCurrent(String tplName, Date at);

    /**
     * 按文种取开口版（唯一口）：at 那一刻在位且干净的版里版次数值最大者开口。
     * 「按版次取值」与「整表取优」两个位置问同一刻同一文种，都走这里，两答必同一版。
     * 没有开口版回 null。
     */
    TNslpDocTpl resolveVersion(String tplName, Date at);

    /**
     * 立一版：miss_num 由服务端按 var_num－fill_num 唯一轧法写回，不接手填；
     * 配置不干净逐句点名拒掉。
     */
    TNslpDocTpl saveVersion(TNslpDocTpl tpl, String operator);

    /** 撤版：记成已撤版（情形 1），不进现行清单，但照主键仍查得着、旧文档不回改 */
    int withdrawVersion(Long id, String operator);

    /**
     * 出文（唯一取值口）：照出文那一刻选开口版，逐项点取不到值的应填项；齐了才落一条
     * 出文留档（把当版主键/版次/三个数钉住），有缺项这一纸张不发。
     */
    DocIssueResult issue(DocIssueSubmit submit);
}
