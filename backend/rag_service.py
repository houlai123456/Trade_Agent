"""
RAG 服务 — 股票新闻智能问答
数据源: AKShare stock_news_em（东方财富新闻）
向量库: Qdrant（localhost:6333）
Embedding: BAAI/bge-small-zh-v1.5（本地512维）
LLM: DeepSeek V4
"""

"""
RAG 服务 — 股票新闻智能问答
数据源: AKShare stock_news_em（东方财富新闻）
向量库: Qdrant（localhost:6333）
Embedding: 阿里云 text-embedding-v1（1536维）
LLM: DeepSeek V4
"""

import hashlib
import logging
import os
import re
import time

from dotenv import load_dotenv
from typing import Dict, List, Tuple

import requests
from qdrant_client import QdrantClient
from qdrant_client.http import models as qdrant_models

import akshare as ak

logger = logging.getLogger(__name__)

# ==================== 配置 ====================

load_dotenv(os.path.join(os.path.dirname(__file__), "..", ".env"))

DEEPSEEK_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = "deepseek-v4-flash"

ALIYUN_KEY = os.getenv("ALIYUN_API_KEY", "")
ALIYUN_EMBEDDING_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding"
ALIYUN_EMBEDDING_MODEL = "tongyi-embedding-vision-plus-2026-03-06"

QDRANT_URL = "http://localhost:6333"
COLLECTION_NAME = "stock_news"

EMBED_DIM = 1152
EMBED_BATCH = 10

CHUNK_MAX_CHARS = 500
CHUNK_OVERLAP = 50


# ==================== Embedding（阿里云 API）====================

def get_embedding(text: str, retries: int = 3) -> List[float]:
    """单条文本 embedding（tongyi-embedding-vision-plus）"""
    for attempt in range(retries):
        try:
            r = requests.post(
                ALIYUN_EMBEDDING_URL,
                headers={
                    "Authorization": f"Bearer {ALIYUN_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": ALIYUN_EMBEDDING_MODEL,
                    "input": {"contents": [{"text": text}]},
                    "parameters": {"auto_truncation": True},
                },
                timeout=15,
            )
            r.raise_for_status()
            return r.json()["output"]["embeddings"][0]["embedding"]
        except Exception as e:
            logger.warning(f"Embedding 失败 (尝试 {attempt+1}/{retries}): {e}")
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"Embedding 全部失败: {text[:50]}")


def get_embeddings_batch(texts: List[str], retries: int = 3) -> List[List[float]]:
    """批量 embedding（tongyi-embedding-vision-plus）"""
    for attempt in range(retries):
        try:
            r = requests.post(
                ALIYUN_EMBEDDING_URL,
                headers={
                    "Authorization": f"Bearer {ALIYUN_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": ALIYUN_EMBEDDING_MODEL,
                    "input": {"contents": [{"text": t} for t in texts]},
                    "parameters": {"auto_truncation": True},
                },
                timeout=30,
            )
            r.raise_for_status()
            data = r.json()
            results = [None] * len(texts)
            for emb in data["output"]["embeddings"]:
                results[emb.get("index", 0)] = emb["embedding"]
            return results
        except Exception as e:
            logger.warning(f"批量 Embedding 失败 (尝试 {attempt+1}/{retries}): {e}")
            if attempt < retries - 1:
                time.sleep(2 ** attempt)
    raise RuntimeError("批量 Embedding 全部失败")


# ==================== 文本切片 ====================

def chunk_text(content: str, title: str, source: str = "") -> List[Dict]:
    """按句子边界切片"""
    sentences = re.split(r"(?<=[。！？\n])", content)
    chunks = []
    current = ""
    for sent in sentences:
        sent = sent.strip()
        if not sent:
            continue
        if len(current) + len(sent) > CHUNK_MAX_CHARS and current:
            chunks.append(current.strip())
            current = current[-CHUNK_OVERLAP:] if CHUNK_OVERLAP > 0 else ""
        current += sent
    if current.strip():
        chunks.append(current.strip())

    return [
        {
            "content": text,
            "chunk_index": i,
            "total_chunks": len(chunks),
            "title": title,
            "source": source,
        }
        for i, text in enumerate(chunks)
    ]


# ==================== RAG 服务 ====================

class RAGService:
    def __init__(self):
        self.qdrant = QdrantClient(url=QDRANT_URL)
        self._init_collection()

    def _init_collection(self):
        """检查/创建 Qdrant collection（维度不匹配则重建）"""
        collections = self.qdrant.get_collections().collections
        exists = any(c.name == COLLECTION_NAME for c in collections)
        if not exists:
            self._create_collection()
            return
        info = self.qdrant.get_collection(COLLECTION_NAME)
        cfg = info.config.params.vectors
        if cfg.size != EMBED_DIM:
            logger.warning(f"维度不匹配 {cfg.size}->{EMBED_DIM}，删除重建")
            self.qdrant.delete_collection(COLLECTION_NAME)
            self._create_collection()
        else:
            logger.info(f"Qdrant collection 已就绪: {COLLECTION_NAME}")

    def _create_collection(self):
        self.qdrant.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=qdrant_models.VectorParams(
                size=EMBED_DIM, distance=qdrant_models.Distance.COSINE
            ),
        )
        logger.info(f"创建 Qdrant collection: {COLLECTION_NAME}")

    def get_collection_info(self) -> Dict:
        try:
            info = self.qdrant.get_collection(COLLECTION_NAME)
            return {"vectors_count": info.points_count, "status": str(info.status)}
        except Exception as e:
            return {"error": str(e)}

    # ---------- 数据写入 ----------

    def ingest_stock_news(self, stock_code: str, stock_name: str) -> int:
        """拉取并索引一只股票的新闻"""
        try:
            df = ak.stock_news_em(symbol=stock_code)
        except Exception as e:
            logger.warning(f"[{stock_name}] 拉取新闻失败: {e}")
            return 0

        if df is None or df.empty:
            return 0

        all_chunks = []
        for _, row in df.iterrows():
            title = str(row.get("新闻标题", ""))
            content = str(row.get("新闻内容", ""))
            if not content or content == "nan":
                continue
            all_chunks.extend(chunk_text(content, title, source="东方财富"))

        if not all_chunks:
            return 0

        points = []
        for i in range(0, len(all_chunks), EMBED_BATCH):
            batch = all_chunks[i : i + EMBED_BATCH]
            texts = [b["content"] for b in batch]
            try:
                vectors = get_embeddings_batch(texts)
            except Exception as e:
                logger.error(f"[{stock_name}] Embedding 失败: {e}")
                continue
            for j, vec in enumerate(vectors):
                if vec is None:
                    continue
                c = batch[j]
                chunk_id = hashlib.md5(c["content"].encode()).hexdigest()
                points.append(
                    qdrant_models.PointStruct(
                        id=chunk_id,
                        vector=vec,
                        payload={
                            "stock_code": stock_code,
                            "stock_name": stock_name,
                            "title": c["title"],
                            "content": c["content"],
                            "date": "",
                            "source": c["source"],
                        },
                    )
                )

        if points:
            self.qdrant.upsert(
                collection_name=COLLECTION_NAME, points=points, wait=True
            )
            logger.info(f"[{stock_name}] 写入 {len(points)} 条片段")
        return len(points)

    def ingest_stock_list(self, stock_list: List[Tuple[str, str]]) -> Dict:
        total = 0
        errors = []
        for code, name in stock_list:
            try:
                c = self.ingest_stock_news(code, name)
                total += c
                logger.info(f"  → {name}({code}) 索引 {c} 条，累计 {total}")
            except Exception as e:
                logger.error(f"[{name}] 索引失败: {e}")
                errors.append(f"{name}({code}): {e}")
            time.sleep(1)
        return {"total_chunks": total, "errors": errors, "stock_count": len(stock_list)}

    # ---------- 文档上传 ----------

    def ingest_document(self, content: str, filename: str, stock_name: str = "") -> int:
        """手动上传的文档内容索引"""
        chunks = chunk_text(content, title=filename, source=f"上传文档/{filename}")
        if not chunks:
            return 0

        points = []
        for i in range(0, len(chunks), EMBED_BATCH):
            batch = chunks[i : i + EMBED_BATCH]
            texts = [b["content"] for b in batch]
            try:
                vectors = get_embeddings_batch(texts)
            except Exception as e:
                logger.error(f"[{filename}] Embedding 失败: {e}")
                continue
            for j, vec in enumerate(vectors):
                if vec is None:
                    continue
                c = batch[j]
                chunk_id = hashlib.md5(c["content"].encode()).hexdigest()
                points.append(
                    qdrant_models.PointStruct(
                        id=chunk_id,
                        vector=vec,
                        payload={
                            "stock_code": "",
                            "stock_name": stock_name or filename,
                            "title": c["title"],
                            "content": c["content"],
                            "date": "",
                            "source": c["source"],
                        },
                    )
                )

        if points:
            self.qdrant.upsert(
                collection_name=COLLECTION_NAME, points=points, wait=True
            )
            logger.info(f"[{filename}] 写入 {len(points)} 条片段")
        return len(points)

    # ---------- 检索 ----------

    def search(self, query: str, top_k: int = 5) -> List[Dict]:
        """向量检索"""
        if not query.strip():
            return []
        vector = get_embedding(query)
        hits = self.qdrant.query_points(
            collection_name=COLLECTION_NAME,
            query=vector,
            limit=top_k,
            with_payload=True,
        ).points
        return [
            {
                "score": round(hit.score, 4),
                "stock_code": hit.payload.get("stock_code", ""),
                "stock_name": hit.payload.get("stock_name", ""),
                "title": hit.payload.get("title", ""),
                "content": hit.payload.get("content", ""),
                "date": hit.payload.get("date", ""),
                "source": hit.payload.get("source", ""),
            }
            for hit in hits
        ]

    # ---------- 问答（带自动拉取） ----------

    def ask(self, question: str, top_k: int = 5, history: List[Dict] = None) -> Dict:
        """问答：先检索缓存，结果不够好则自动拉取对应股票新闻"""
        context, sources = self.prepare_context(question, top_k=top_k)

        if not context:
            return {"answer": "暂无相关数据，请换个问题试试。", "sources": []}

        answer = self._call_llm(question, context, history=history)
        return {"answer": answer, "sources": sources}

    def ask_stream(self, question: str, top_k: int = 5, history: List[Dict] = None):
        """流式问答：先检索，再逐 chunk 流式返回 LLM 回答，最后 yield sources"""
        context, sources = self.prepare_context(question, top_k=top_k)

        if not context:
            yield ("answer", "暂无相关数据，请换个问题试试。")
            yield ("sources", sources)
            return

        full = []
        for chunk in self._call_llm_stream(question, context, history=history):
            full.append(chunk)
            yield ("answer", chunk)

        yield ("sources", sources)

    def _answer(self, question: str, docs: List[Dict], history: List[Dict] = None) -> Dict:
        """用检索结果生成 LLM 回答"""
        context = "\n\n---\n\n".join(
            f"[{d['stock_name']}({d['stock_code']})] {d['title']}\n{d['content']}"
            for d in docs
        )
        answer = self._call_llm(question, context, history=history)
        return {
            "answer": answer,
            "sources": [
                {"stock_name": d["stock_name"], "title": d["title"], "score": d["score"]}
                for d in docs
            ],
        }

    _stock_map: Dict[str, str] = {}

    def _extract_stocks(self, question: str) -> List[Tuple[str, str]]:
        """从问题中提取股票名称 → (代码, 名称)"""
        if not self._stock_map:
            self._load_stock_map()
        hits = []
        for name, code in self._stock_map.items():
            if name in question:
                hits.append((code, name))
        return hits

    def _load_stock_map(self):
        """加载 A 股名称→代码映射"""
        try:
            df = ak.stock_zh_a_spot()
            if df is not None and not df.empty:
                for _, row in df.iterrows():
                    code = str(row.get("代码", "")).strip()
                    name = str(row.get("名称", "")).strip()
                    if code and name:
                        # ak.stock_zh_a_spot 返回格式: sz000001 或 600519
                        # 只取后6位纯数字代码
                        code = re.sub(r"\D", "", code)
                        if len(code) == 6:
                            self._stock_map[name] = code
                logger.info(f"加载股票映射 {len(self._stock_map)} 条")
        except Exception as e:
            logger.warning(f"加载股票列表失败: {e}")

    def prepare_context(self, question: str, top_k: int = 5) -> Tuple[str, List[Dict]]:
        """检索上下文，返回 (context_string, sources)"""
        docs = self.search(question, top_k=top_k)
        stocks = self._extract_stocks(question)

        need_fetch = False
        if stocks:
            stock_names_in_docs = {d["stock_name"] for d in docs}
            for _, name in stocks:
                if name not in stock_names_in_docs:
                    need_fetch = True
                    break
        elif not docs:
            need_fetch = False

        if need_fetch:
            for code, name in stocks:
                logger.info(f"[auto-fetch] 检测到股票 {name}({code})，自动拉取新闻")
                try:
                    self.ingest_stock_news(code, name)
                except Exception as e:
                    logger.warning(f"[auto-fetch] {name} 拉取失败: {e}")
                time.sleep(0.5)
            docs = self.search(question, top_k=top_k)

        context = "\n\n---\n\n".join(
            f"[{d['stock_name']}({d['stock_code']})] {d['title']}\n{d['content']}"
            for d in docs
        ) if docs else ""
        sources = [
            {"stock_name": d["stock_name"], "title": d["title"], "score": d["score"]}
            for d in docs
        ]
        return context, sources

    def _build_messages(self, question: str, context: str, history: List[Dict] = None) -> List[Dict]:
        messages = [
            {"role": "system", "content": (
                "你是专业的股票投资分析助手。请基于提供的新闻信息回答问题。\n"
                "要求：\n"
                "1. 只基于提供的信息分析，不要编造数据\n"
                "2. 如果信息不足以回答，请明确说明\n"
                "3. 引用具体信息时标注来源股票名称\n"
                "4. 用中文回答，简洁专业"
            )}
        ]
        if history:
            for h in history:
                if h.get("role") in ("user", "assistant"):
                    messages.append({"role": h["role"], "content": h["content"]})
        if context:
            messages.append({"role": "user", "content": f"相关信息：\n{context}\n\n问题：{question}"})
        else:
            messages.append({"role": "user", "content": question})
        return messages

    def _call_llm(self, question: str, context: str, history: List[Dict] = None) -> str:
        messages = self._build_messages(question, context, history)
        r = requests.post(
            "https://api.deepseek.com/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {DEEPSEEK_KEY}",
                "Content-Type": "application/json",
            },
            json={"model": DEEPSEEK_MODEL, "messages": messages, "temperature": 0.3, "max_tokens": 2000},
            timeout=30,
        )
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]

    def _call_llm_stream(self, question: str, context: str, history: List[Dict] = None):
        """流式调用 DeepSeek，逐 chunk yield 文本"""
        messages = self._build_messages(question, context, history)
        r = requests.post(
            "https://api.deepseek.com/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {DEEPSEEK_KEY}",
                "Content-Type": "application/json",
            },
            json={"model": DEEPSEEK_MODEL, "messages": messages, "temperature": 0.3, "max_tokens": 2000, "stream": True},
            timeout=60,
            stream=True,
        )
        r.raise_for_status()
        for line in r.iter_lines():
            if not line:
                continue
            line = line.decode("utf-8", errors="replace")
            if line.startswith("data: "):
                payload = line[6:]
                if payload.strip() == "[DONE]":
                    break
                try:
                    import json
                    data = json.loads(payload)
                    delta = data.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content", "")
                    if content:
                        yield content
                except json.JSONDecodeError:
                    continue
