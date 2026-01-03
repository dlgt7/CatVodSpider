# coding=utf-8
#!/usr/bin/python

"""
作者 [你的名字或昵称] 🚓 内容均从互联网收集而来 仅供交流学习使用 版权归原创者所有 如侵犯了您的权益 请通知作者 将及时删除侵权内容
                    ====================YourSignature====================
"""

from Crypto.Util.Padding import unpad, pad
from urllib.parse import unquote, quote, urljoin
from Crypto.Cipher import ARC4, AES
from bs4 import BeautifulSoup
import urllib.request
import urllib.parse
import binascii
import requests
import base64
import json
import time
import sys
import re
import os

sys.path.append('..')

from base.spider import Spider

# ==================== 全局配置区 ====================
base_url = "https://example.com"          # 主域名
header_common = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Referer': base_url + '/'
}

# 备用解析接口
fallback_jx = "https://vip.bljiex.com/?v="

class TemplateSpider(Spider):
    global base_url, header_common

    def getName(self):
        return "短剧源模板"

    def init(self, extend=""):
        """初始化"""
        pass

    def isVideoFormat(self, url):
        return False

    def manualVideoCheck(self):
        return False

    # ==================== 路径修复工具 ====================
    def fixUrl(self, url):
        if not url: return ""
        if url.startswith('//'): return "https:" + url
        return urljoin(base_url, url)

    # ==================== 通用提取工具 ====================
    def extract_middle_text(self, text, start_str, end_str, mode=0, pattern='', group=0):
        """
        mode:
            0 - 简单提取一次
            1 - 正则提取所有并用空格连接
            2 - 正则提取所有并用$$$连接
            3 - 多块提取 + 正则（用于播放列表）
        """
        if mode == 3:
            blocks = []
            temp = text
            while True:
                s = temp.find(start_str)
                if s == -1: break
                e = temp.find(end_str, s + len(start_str))
                if e == -1: break
                blocks.append(temp[s + len(start_str):e])
                temp = temp.replace(start_str + temp[s + len(start_str):e] + end_str, '', 1)

            if not blocks: return ""

            lines = []
            for block in blocks:
                matches = re.findall(pattern, block)
                parts = []
                for m in matches:
                    title = m[1] if isinstance(m, tuple) else m
                    url_part = m[0] if isinstance(m, tuple) else m
                    full_url = self.fixUrl(url_part)
                    parts.append(f"{title}${full_url}")
                if parts:
                    lines.append("#".join(parts))
            return "$$$".join(lines) if lines else ""

        s = text.find(start_str)
        if s == -1: return ""
        e = text.find(end_str, s + len(start_str))
        if e == -1: return ""
        content = text[s + len(start_str):e].replace("\\\\", "")

        if mode == 0: return content
        if mode in (1, 2):
            matches = re.findall(pattern, content)
            if not matches: return ""
            if mode == 1: return " ".join([m[group] if isinstance(m, tuple) else m for m in matches])
            if mode == 2: return "$$$".join(matches)
        return content

    # ==================== 首页分类 ====================
    def homeContent(self, filter):
        result = {}
        # 建议根据目标站实际 F12 查看 type_id
        classes = [
            {"type_id": "1", "type_name": "霸总"},
            {"type_id": "2", "type_name": "逆袭"},
            {"type_id": "3", "type_name": "战神"},
            {"type_id": "4", "type_name": "重生"}
        ]
        result["class"] = classes
        return result

    def homeVideoContent(self):
        return {"list": []}

    # ==================== 分类页 ====================
    def categoryContent(self, tid, pg, filter, ext):
        result = {}
        videos = []
        page = int(pg) if pg else 1
        url = f"{base_url}/list/{tid}-{page}.html"

        try:
            rsp = requests.get(url, headers=header_common, timeout=10)
            rsp.encoding = 'utf-8'
            soup = BeautifulSoup(rsp.text, "lxml")

            # 这里的选择器需根据实际站点微调
            items = soup.find_all('div', class_=re.compile('item|vod|list'))

            for item in items:
                a_tag = item.find('a')
                img_tag = item.find('img')
                if not a_tag: continue
                
                title = a_tag.get('title') or a_tag.get_text()
                vod_id = a_tag['href']
                pic = img_tag.get('data-original') or img_tag.get('src') if img_tag else ""
                
                remark = ""
                remark_tag = item.find(class_=re.compile('remarks|note|tag'))
                if remark_tag: remark = remark_tag.get_text().strip()

                videos.append({
                    "vod_id": self.fixUrl(vod_id),
                    "vod_name": title.strip(),
                    "vod_pic": self.fixUrl(pic),
                    "vod_remarks": remark
                })
        except Exception as e:
            pass

        result["list"] = videos
        result["page"] = page
        result["pagecount"] = 9999
        result["limit"] = 90
        result["total"] = 999999
        return result

    # ==================== 详情页 ====================
    def detailContent(self, ids):
        did = ids[0]
        result = {}
        videos = []

        try:
            rsp = requests.get(did, headers=header_common, timeout=10)
            rsp.encoding = 'utf-8'
            html = rsp.text
            soup = BeautifulSoup(html, "lxml")

            title = soup.find('h1').get_text().strip() if soup.find('h1') else "未知"
            # 移除“集多”标识，改为通用的提示
            content = "剧情简介：" + (self.extract_middle_text(html, '简介', '</div>', 0).strip() or "暂无")

            # 播放列表处理
            # 逻辑：查找所有的播放列表ul，适配多线路
            play_sources = []
            play_lists = []
            
            tabs = soup.find_all('div', class_=re.compile('tab-item|line-title'))
            lists = soup.find_all('ul', class_=re.compile('playlist|list-item'))

            if lists:
                for i, ul in enumerate(lists):
                    source_name = tabs[i].get_text().strip() if i < len(tabs) else f"线路{i+1}"
                    links = ul.find_all('a')
                    itms = [f"{a.get_text()}${self.fixUrl(a['href'])}" for a in links if a.get('href')]
                    if itms:
                        play_sources.append(source_name)
                        play_lists.append("#".join(itms))

            vod_item = {
                "vod_id": did,
                "vod_name": title,
                "vod_content": content,
                "vod_play_from": "$$$".join(play_sources) if play_sources else "默认",
                "vod_play_url": "$$$".join(play_lists) if play_lists else ""
            }
            videos.append(vod_item)
        except:
            videos.append({"vod_id": did, "vod_name": "加载失败", "vod_play_from": "None", "vod_play_url": ""})

        result["list"] = videos
        return result

    # ==================== 播放页 ====================
    def playerContent(self, flag, id, vipFlags):
        result = {}
        try:
            # 很多短剧网站在播放页源码里直接写了 var player_data = {...}
            rsp = requests.get(id, headers=header_common, timeout=10)
            html = rsp.text
            
            # 尝试提取 m3u8 地址
            url = re.search(r'url["\']\s*:\s*["\'](.*?\.m3u8.*?)["\']', html)
            if not url:
                url = re.search(r'["\'](http.*?\.m3u8.*?)["\']', html)
            
            play_url = url.group(1).replace('\\', '') if url else id

            result["parse"] = 0
            result["playUrl"] = ""
            result["url"] = play_url
            result["header"] = header_common
        except:
            result["url"] = id
        return result

    # ==================== 搜索 ====================
    def searchContentPage(self, key, quick, page):
        result = {}
        videos = []
        pg = int(page) if page else 1
        # 适配搜索接口
        url = f"{base_url}/index.php/vod/search/page/{pg}/wd/{quote(key)}.html"

        try:
            rsp = requests.get(url, headers=header_common, timeout=10)
            soup = BeautifulSoup(rsp.text, "lxml")
            items = soup.find_all('div', class_=re.compile('item|vod|list'))
            for item in items:
                a = item.find('a')
                if not a: continue
                videos.append({
                    "vod_id": self.fixUrl(a['href']),
                    "vod_name": a.get_text().strip(),
                    "vod_pic": self.fixUrl(item.find('img')['src'] if item.find('img') else ""),
                    "vod_remarks": ""
                })
        except:
            pass

        result["list"] = videos
        result["page"] = pg
        result["pagecount"] = 9999
        return result

    def searchContent(self, key, quick, pg="1"):
        return self.searchContentPage(key, quick, pg)

    # ==================== 本地代理 ====================
    def localProxy(self, param):
        return None
