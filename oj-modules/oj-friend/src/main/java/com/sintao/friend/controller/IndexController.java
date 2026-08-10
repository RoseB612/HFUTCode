package com.sintao.friend.controller;

import com.sintao.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IndexController {

    @GetMapping("/")
    public R<IndexInfo> index() {
        return R.ok(new IndexInfo(
                "oj-friend",
                "UP",
                List.of(
                        "/resume/health",
                        "/resume/upload",
                        "/resume/list",
                        "/question/semiLogin/list",
                        "/training/profile"
                )
        ));
    }

    public record IndexInfo(String service, String status, List<String> endpoints) {
    }
}
