"""Leased, identified connections owned by the authenticated backend, never by a browser."""
import asyncio
import contextlib
import time
from dataclasses import dataclass, field


@dataclass
class Connection:
    owner: str
    model: str
    effort: str
    session: object
    touched: float = field(default_factory=time.monotonic)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    task: object = None
    ready: bool = False
    closing: bool = False


class ConversationSessions:
    def __init__(self, factory, limit=16, lease=120):
        self.factory, self.limit, self.lease = factory, limit, lease
        self.entries = {}

    async def open(self, ident, owner, model, effort, messages, choose):
        entry = self.entries.get(ident)
        if entry:
            if (entry.owner, entry.model, entry.effort) != (owner, model, effort):
                raise ValueError('Session identity conflict.')
            async with entry.lock:
                if not entry.ready or entry.closing:
                    raise ValueError('Session is unavailable.')
                entry.touched = time.monotonic()
                return entry
        if len(self.entries) >= self.limit:
            raise OverflowError('Resident session limit reached.')
        entry = Connection(owner, model, effort, self.factory())
        self.entries[ident] = entry
        async with entry.lock:
            entry.task = asyncio.current_task()
            try:
                await entry.session.__aenter__()
                chosen = await choose(entry.session, model, effort)
                await entry.session.prepare(messages, chosen['model'], effort)
                entry.ready = True
                entry.touched = time.monotonic()
                return entry
            except BaseException:
                if self.entries.get(ident) is entry:
                    self.entries.pop(ident)
                await entry.session.close()
                raise
            finally:
                entry.task = None

    @contextlib.asynccontextmanager
    async def use(self, ident):
        entry = self.entries.get(ident)
        if not entry or not entry.ready or entry.closing:
            raise KeyError('Session not ready or expired.')
        if entry.lock.locked():
            raise OverflowError('A turn is already active in this session.')
        async with entry.lock:
            entry.task = asyncio.current_task()
            entry.touched = time.monotonic()
            try:
                yield entry.session
            except BaseException:
                try:
                    await asyncio.shield(entry.session.interrupt())
                except BaseException:
                    entry.ready = False
                    if self.entries.get(ident) is entry:
                        self.entries.pop(ident)
                    await entry.session.close()
                raise
            finally:
                entry.task = None
                entry.touched = time.monotonic()

    async def close(self, ident):
        entry = self.entries.pop(ident, None)
        if not entry:
            return
        entry.closing = True
        if entry.task and entry.task is not asyncio.current_task():
            entry.task.cancel()
        async with entry.lock:
            await entry.session.close()

    async def reap(self):
        now = time.monotonic()
        for ident, entry in list(self.entries.items()):
            if now - entry.touched > self.lease:
                await self.close(ident)

    async def shutdown(self):
        await asyncio.gather(*(self.close(ident) for ident in list(self.entries)))
