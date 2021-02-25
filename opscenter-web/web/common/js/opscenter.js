const OpsService = Lealone.getService("ops_service");
const AdminService = Lealone.getService("admin_service");
const QueryService = Lealone.getService("query_service");
const DatabaseService = Lealone.getService("database_service");

const OpsCenter = {};

OpsCenter.i18n = {
    loadAndMount(app, appName) {
        OpsService.readTranslations("zh_CN", data => {
            var text = data.text;
            var newText = OpsCenter.i18n.parse(text);
            app.mixin({
                data() { return { i18n: text,  text: newText } },
            });
            app.mount(appName);
        });
    },
    parse(text) {
        var newText = {};
        // 把点号分隔的key变成嵌套对象，然后在膜拜引擎中像这样使用: {{ text.a.b.c }}
        for(var key in text) { 
            var a = key.split('.');
            if(a.length < 2) {
                newText[key] = text[key];
                continue;
            }
            var len = a.length - 1;
            a.reduce((o, k, i) => {
                if(i == len)
                    o[k] = text[key];
                else {
                    if (!o[k]) o[k] = {};
                    else if(o[k] instanceof Object) {
                        return o[k]; 
                    } else {
                        // var oldValue = o[k];
                        // o[k] = {};
                        // o[k]["$text$"] = oldValue;
                    }
                    return o[k];
                }
            }, newText);
        }
        // 支持两种访问方式:
        // {{ i18n['toolbar.autoSelect.on'] }}
        // {{ text.toolbar.autoSelect }}
        // 因为vue不能像这样使用{{ text.toolbar.autoSelect.on }}
        return newText;
    }
}
var mount = function(app, appName) {
    // 混入全局的logout方法
    app.mixin({
        methods: {
            logout() {
                OpsService.logout(lealone.currentUser, _=>(location.href = "/admin/index.html"));
            }
        }
    });
    OpsCenter.i18n.loadAndMount(app, appName);
}

