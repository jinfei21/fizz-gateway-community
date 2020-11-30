/*
 *  Copyright (C) 2020 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package we.plugin.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import we.util.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import we.util.ThreadContext;

import java.util.*;

/**
 * @author hongqiaowei
 */

public class ServiceConfig {

    private static final Logger         log            = LoggerFactory.getLogger(ServiceConfig.class);

    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();

    private static final String         mpps           = "$mpps";

    public String id;

    @JsonIgnore
    public Map<Integer, ApiConfig> apiConfigMap = new HashMap<>(32);

    public Map<String, EnumMap<HttpMethod, GatewayGroup2appsToApiConfig>> path2methodToApiConfigMapMap = new HashMap<>(6);

    public ServiceConfig(String id) {
        this.id = id;
    }

    public void add(ApiConfig ac) {
        apiConfigMap.put(ac.id, ac);
        EnumMap<HttpMethod, GatewayGroup2appsToApiConfig> method2apiConfigMap = path2methodToApiConfigMapMap.get(ac.path);
        if (method2apiConfigMap == null) {
            method2apiConfigMap = new EnumMap<>(HttpMethod.class);
            GatewayGroup2appsToApiConfig gatewayGroup2appsToApiConfig = new GatewayGroup2appsToApiConfig();
            gatewayGroup2appsToApiConfig.add(ac);
            method2apiConfigMap.put(ac.method, gatewayGroup2appsToApiConfig);
            path2methodToApiConfigMapMap.put(ac.path, method2apiConfigMap);
        } else {
            GatewayGroup2appsToApiConfig gatewayGroup2appsToApiConfig = method2apiConfigMap.get(ac.method);
            if (gatewayGroup2appsToApiConfig == null) {
                gatewayGroup2appsToApiConfig = new GatewayGroup2appsToApiConfig();
                method2apiConfigMap.put(ac.method, gatewayGroup2appsToApiConfig);
            }
            gatewayGroup2appsToApiConfig.add(ac);
        }
        log.info("add " + ac);
    }

    public void remove(ApiConfig ac) {
        ApiConfig remove = apiConfigMap.remove(ac.id);
        Map<HttpMethod, GatewayGroup2appsToApiConfig> method2apiConfigMap = path2methodToApiConfigMapMap.get(ac.path);
        if (method2apiConfigMap == null) {
            log.info("no config to delete for " + ac.service + ' ' + ac.path);
        } else {
            GatewayGroup2appsToApiConfig gatewayGroup2appsToApiConfig = method2apiConfigMap.get(ac.method);
            if (gatewayGroup2appsToApiConfig == null) {
                log.info("no config to delete for " + ac.service + ' ' + ac.method + ' ' + ac.path);
            } else {
                log.info(id + " remove " + ac);
                gatewayGroup2appsToApiConfig.remove(ac);
            }
        }
    }

    public void update(ApiConfig ac) {
        ApiConfig prev = apiConfigMap.put(ac.id, ac);
        log.info(prev + " is updated by " + ac + " in api config map");
        EnumMap<HttpMethod, GatewayGroup2appsToApiConfig> method2apiConfigMap = path2methodToApiConfigMapMap.get(ac.path);
        if (method2apiConfigMap == null) {
            method2apiConfigMap = new EnumMap<>(HttpMethod.class);
            GatewayGroup2appsToApiConfig gatewayGroup2appsToApiConfig = new GatewayGroup2appsToApiConfig();
            gatewayGroup2appsToApiConfig.add(ac);
            method2apiConfigMap.put(ac.method, gatewayGroup2appsToApiConfig);
            path2methodToApiConfigMapMap.put(ac.path, method2apiConfigMap);
        } else {
            GatewayGroup2appsToApiConfig gatewayGroup2appsToApiConfig = method2apiConfigMap.get(ac.method);
            if (gatewayGroup2appsToApiConfig == null) {
                gatewayGroup2appsToApiConfig = new GatewayGroup2appsToApiConfig();
                method2apiConfigMap.put(ac.method, gatewayGroup2appsToApiConfig);
                gatewayGroup2appsToApiConfig.add(ac);
            } else {
                log.info(id + " update " + ac);
                gatewayGroup2appsToApiConfig.update(ac);
            }
        }
    }

    @JsonIgnore
    public ApiConfig getApiConfig(HttpMethod method, String path, String gatewayGroup, String app) {
//      GatewayGroup2appsToApiConfig r = getApiConfig0(method, path);
        GatewayGroup2appsToApiConfig r = getApiConfig2(method, path);
        if (r == null) {
            return null;
        }
        if (StringUtils.isBlank(app)) {
            app = App.ALL_APP;
        }
        return r.get(gatewayGroup, app);
    }

    private GatewayGroup2appsToApiConfig getApiConfig2(HttpMethod method, String reqPath) {

        List<String> matchPathPatterns = ThreadContext.getArrayList(mpps, String.class);

        Set<Map.Entry<String, EnumMap<HttpMethod, GatewayGroup2appsToApiConfig>>> es = path2methodToApiConfigMapMap.entrySet();
        for (Map.Entry<String, EnumMap<HttpMethod, GatewayGroup2appsToApiConfig>> e : es) {
            String pathPattern = e.getKey();
            if (ApiConfig.isAntPathPattern(pathPattern)) {
                if (antPathMatcher.match(pathPattern, reqPath)) {
                    matchPathPatterns.add(pathPattern);
                }
            } else if (reqPath.equals(pathPattern)) {
                return getApiConfig1(method, e.getValue());
            }
        }
        if (matchPathPatterns.isEmpty()) {
            return null;
        } else {
            Collections.sort(matchPathPatterns, antPathMatcher.getPatternComparator(reqPath));
            String bestPattern = matchPathPatterns.get(0);
            return getApiConfig1(method, path2methodToApiConfigMapMap.get(bestPattern));
        }
    }

    private GatewayGroup2appsToApiConfig getApiConfig0(HttpMethod method, String path) {
        while (true) {
            EnumMap<HttpMethod, GatewayGroup2appsToApiConfig> method2apiConfigMap = path2methodToApiConfigMapMap.get(path);
            if (method2apiConfigMap == null) {
                int i = path.lastIndexOf(Constants.Symbol.FORWARD_SLASH);
                if (i == 0) {
                    method2apiConfigMap = path2methodToApiConfigMapMap.get(Constants.Symbol.FORWARD_SLASH_STR);
                    if (method2apiConfigMap == null) {
                        return null;
                    } else {
                        return getApiConfig1(method, method2apiConfigMap);
                    }
                } else {
                    path = path.substring(0, i);
                }
            } else {
                return getApiConfig1(method, method2apiConfigMap);
            }
        }
    }

    private GatewayGroup2appsToApiConfig getApiConfig1(HttpMethod method, EnumMap<HttpMethod, GatewayGroup2appsToApiConfig> method2apiConfigMap) {
        GatewayGroup2appsToApiConfig r = method2apiConfigMap.get(method);
        if (r == null) {
            return method2apiConfigMap.get(HttpMethod.X);
        } else {
            return r;
        }
    }
}
