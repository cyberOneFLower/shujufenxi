package com.arb.monitor.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 生产环境将前端静态资源打入 JAR 后，浏览器直接访问 /vol 等路由时需回到 index.html。
 */
@Controller
public class SpaController {

  @GetMapping({"/", "/login", "/vol", "/blacklist", "/settings", "/latency"})
  public String spa() {
    return "forward:/index.html";
  }
}
