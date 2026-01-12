/**
 * 终极通用 JS 影视源模板（Drpy/TVBox/影视TV 专用） - 2026.01.13 完整版
 * 功能：动态域名、搜索自动编码、网盘自动识别、磁力嗅探、多线去重
 */

var rule = {
    title: '终极通用模板[2026]',
    host: 'https://www.example.com', // 替换为目标域名
    homeUrl: '/',
    url: '/vodshow/fyclass--------fypage---.html',
    searchUrl: '/vodsearch/**----------fypage---.html',
    searchable: 2,
    quickSearch: 1,
    filterable: 1,
    headers: {
        'User-Agent': 'MOBILE_UA',
        'Referer': 'https://www.google.com'
    },
    timeout: 10000,
    class_name: '电影&电视剧&动漫&综艺&短剧&磁力',
    class_url: '1&2&3&4&5&6',
    
    // ==================== 1. 防封动态域名 (hostJs) ====================
    hostJs: $js.toString(() => {
        try {
            let html = request(HOST, {timeout: 8000});
            // 匹配 meta 刷新或 a 标签跳转
            let real = pdfh(html, 'meta[http-equiv=refresh]&&content') || pdfh(html, 'a[href^=http]&&href') || '';
            if (real) {
                let match = real.match(/http[^'"]+/);
                if (match && match[0] !== HOST) {
                    HOST = match[0].replace(/\/+$/, '');
                }
            }
        } catch (e) {
            log('hostJs 自动寻址异常: ' + e);
        }
    }),

    // ==================== 2. 预处理 (pre) ====================
    pre: $js.toString(() => {
        if (/(search|forum)/i.test(input)) {
            try {
                let h = request(HOST);
                // 自动抓取 Discuz! 类论坛的 formhash
                let fh = pdfh(h, 'input[name="formhash"]&&value');
                if (fh) setItem('formhash', fh);
                log('预处理：FormHash 已缓存');
            } catch (e) {}
        }
    }),

    // ==================== 3. 搜索增强 (GBK/Post 自动适配) ====================
    搜索: $js.toString(() => {
        try {
            let d = [];
            let html = request(input);
            // 自动 GBK 识别转换
            if (/charset=gbk|gb2312/i.test(html)) {
                html = request(input, { headers: { 'Accept-Charset': 'gbk' } });
            }

            let items = pdfa(html, 'body&&.vodlist li||.list li||ul li:has(a)');
            items.forEach(it => {
                let title = pdfh(it, 'a&&title') || pdfh(it, 'img&&alt') || pdfh(it, 'a&&Text');
                if (title && title.includes(KEY)) {
                    d.push({
                        title: title,
                        img: pd(it, 'img&&src||img&&data-original', HOST),
                        desc: pdfh(it, '.remark||.note&&Text'),
                        url: pd(it, 'a&&href', HOST)
                    });
                }
            });
            setResult(d);
        } catch (e) {
            log('搜索逻辑异常: ' + e);
        }
    }),

    // ==================== 4. 二级详情 (云盘/磁力/官源三栖) ====================
    二级: $js.toString(() => {
        try {
            VOD = {
                vod_name: pdfh(html, 'h1&&Text') || '未知',
                vod_pic: pd(html, 'img&&src', HOST),
                vod_content: pdfh(html, '.content&&Text') || pdfh(html, '#desc&&Text'),
                vod_remarks: pdfh(html, '.remark&&Text') || ''
            };

            let lista = [], listq = [], listm = [], liste = [];
            // 锁定播放列表区域，防止抓到导航栏链接
            let container = pdfh(html, '.playlist||.p-list||.content-playlist||body');
            let links = pdfa(container, 'a[href]');

            let seen = new Set();
            links.forEach(it => {
                let url = pd(it, 'a&&href', HOST);
                if (seen.has(url)) return;
                seen.add(url);
                
                let title = pdfh(it, 'a&&Text') || '播放';

                if (url.includes('aliyundrive.com') || url.includes('alipan.com')) {
                    lista.push(title + '$' + url);
                } else if (url.includes('pan.quark.cn')) {
                    listq.push(title + '$' + url);
                } else if (/^magnet:/.test(url)) {
                    listm.push(title + '$' + url);
                } else if (/^ed2k:|^thunder:/.test(url)) {
                    liste.push(title + '$' + url);
                } else if (/\.html|vodplay/.test(url)) {
                    // 普通在线播放链接
                    liste.push(title + '$' + url);
                }
            });

            LISTS = [];
            if (listm.length) LISTS.push(listm); // 磁力
            if (lista.length) LISTS.push(lista); // 阿里
            if (listq.length) LISTS.push(listq); // 夸克
            if (liste.length) LISTS.push(liste); // 官源/其他
            
            // 多线路引导提示
            if (LISTS.length > 1) {
                LISTS.unshift(['模板提示：多线路已自动分类$http://127.0.0.1:10079/delay/']);
            }
        } catch (e) {
            log('二级解析异常: ' + e);
        }
    }),

    // ==================== 5. 播放解析 (九层兜底逻辑) ====================
    lazy: $js.toString(() => {
        try {
            let inputUrl = input.split('$')[1] || input;

            // 层级1：直链识别
            if (/\.(m3u8|mp4|ts|flv)/i.test(inputUrl)) {
                input = { jx: 0, url: inputUrl, parse: 0, header: rule.headers };
                return;
            }

            // 层级2：网盘推送 (TVBox 内部代理)
            let proxyMap = { 'ali': 'aliyundrive|alipan', 'quark': 'quark' };
            for (let type in proxyMap) {
                if (new RegExp(proxyMap[type]).test(inputUrl)) {
                    let proxyUrl = `http://127.0.0.1:9978/proxy?do=${type}&type=push&url=${encodeURIComponent(inputUrl)}`;
                    input = { jx: 0, url: proxyUrl, parse: 0 };
                    return;
                }
            }

            // 层级3：磁力协议直接返回 (壳子自带解析)
            if (/^magnet:|^ed2k:|^thunder:/.test(inputUrl)) {
                input = { jx: 0, url: inputUrl, parse: 0 };
                return;
            }

            // 层级4：三方解析接口并发尝试
            let jxApi = [
                'https://vip.bljiex.com/?v=',
                'https://jx.aidouer.net/?url=',
                'https://www.ckplayer.vip/jiexi/?url='
            ];
            for (let api of jxApi) {
                try {
                    let res = request(api + inputUrl, {timeout: 3000});
                    let real = res.match(/url[:=]["']([^"']+)/i);
                    if (real && real[1].includes('http')) {
                        input = { jx: 0, url: real[1], parse: 0, header: rule.headers };
                        return;
                    }
                } catch (e) {}
            }

            // 兜底：原始链接交给壳子自动嗅探
            input = { jx: 1, url: inputUrl, parse: 1 };
        } catch (e) {
            log('lazy 播放异常: ' + e);
            input = { jx: 1, url: input, parse: 1 };
        }
    }),

    limit: 40,
    double: true,
    tab_exclude: '排序|猜你喜欢|APP|广告|秒播|追剧周表|今日更新',
    cate_exclude: '留言|APP|官网|专题|排行|导航',
}
