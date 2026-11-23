using System.Collections.Concurrent;
using System.Text.Json;
using System.Threading.Channels;

namespace MobileData.Api;

// Fan-out SSE cho cac thiet bi dang ket noi. Khong luu du lieu nhay cam vao thong bao.
// Neu app da dong can su dung FCM de push tu may chu; SSE chi realtime trong app dang mo.
public sealed class SupportRealtimeEvents
{
    private sealed record Listener(bool IsAdmin, int SubscriberId,
        Channel<SupportSignal> Queue);
    private sealed record SupportSignal(string Kind, long TicketId, string Title, string Message);
    private readonly ConcurrentDictionary<Guid, Listener> _listeners = new();

    public void NotifyAdmins(long ticketId, string title, string message) =>
        Publish(new SupportSignal("admin", ticketId, title, message), true, null);

    public void NotifySubscriber(int subscriberId, long ticketId, string title, string message) =>
        Publish(new SupportSignal("user", ticketId, title, message), false, subscriberId);

    private void Publish(SupportSignal signal, bool adminOnly, int? subscriberId)
    {
        foreach (var client in _listeners.Values)
            if ((adminOnly && client.IsAdmin) || (!adminOnly && !client.IsAdmin &&
                subscriberId == client.SubscriberId))
                client.Queue.Writer.TryWrite(signal);
    }

    public async Task StreamAsync(HttpContext ctx, bool isAdmin, int subscriberId)
    {
        ctx.Response.Headers.CacheControl = "no-cache, no-transform";
        ctx.Response.Headers["X-Accel-Buffering"] = "no";
        ctx.Response.ContentType = "text/event-stream; charset=utf-8";
        var queue = Channel.CreateBounded<SupportSignal>(new BoundedChannelOptions(64)
        {
            SingleReader = true, SingleWriter = false,
            FullMode = BoundedChannelFullMode.DropOldest
        });
        var id = Guid.NewGuid();
        _listeners[id] = new Listener(isAdmin, subscriberId, queue);
        CancellationToken ct = ctx.RequestAborted;
        try
        {
            await ctx.Response.WriteAsync(": connected\n\n", ct);
            await ctx.Response.Body.FlushAsync(ct);
            while (!ct.IsCancellationRequested)
            {
                using var wait = CancellationTokenSource.CreateLinkedTokenSource(ct);
                wait.CancelAfter(TimeSpan.FromSeconds(18));
                try
                {
                    if (!await queue.Reader.WaitToReadAsync(wait.Token)) break;
                    while (queue.Reader.TryRead(out var signal))
                    {
                        string json = JsonSerializer.Serialize(new {
                            kind=signal.Kind, ticketId=signal.TicketId,
                            title=signal.Title, message=signal.Message
                        });
                        await ctx.Response.WriteAsync("data: " + json + "\n\n", ct);
                    }
                }
                catch (OperationCanceledException) when (!ct.IsCancellationRequested)
                {
                    await ctx.Response.WriteAsync(": heartbeat\n\n", ct);
                }
                await ctx.Response.Body.FlushAsync(ct);
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested) { }
        catch (IOException) when (ct.IsCancellationRequested) { }
        finally { _listeners.TryRemove(id, out _); queue.Writer.TryComplete(); }
    }
}
