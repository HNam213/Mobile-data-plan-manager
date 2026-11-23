using System.Collections.Concurrent;

namespace MobileData.Api;

// Nho ngan han tren backend, tach biet theo thue bao; khong luu OTP, so CCCD, token.
// Du lieu se mat khi khoi dong lai backend. Day khong phai lich su ho tro trong SQL Server.
public sealed record ChatTurn(string Role, string Content);
public sealed class ChatConversationMemory
{
    private sealed class Session
    {
        public readonly object Gate = new();
        public readonly List<ChatTurn> Turns = new();
        public DateTimeOffset LastActivity = DateTimeOffset.UtcNow;
    }
    private readonly ConcurrentDictionary<int, Session> _sessions = new();
    private static readonly TimeSpan Expiry = TimeSpan.FromMinutes(30);
    private const int MaxTurns = 12;

    public IReadOnlyList<ChatTurn> Read(int subscriberId)
    {
        if (!_sessions.TryGetValue(subscriberId, out var session)) return Array.Empty<ChatTurn>();
        lock (session.Gate)
        {
            if (DateTimeOffset.UtcNow - session.LastActivity > Expiry)
            {
                session.Turns.Clear();
                return Array.Empty<ChatTurn>();
            }
            session.LastActivity = DateTimeOffset.UtcNow;
            return session.Turns.ToArray();
        }
    }

    public void Append(int subscriberId, string question, string answer)
    {
        var session = _sessions.GetOrAdd(subscriberId, _ => new Session());
        lock (session.Gate)
        {
            if (DateTimeOffset.UtcNow - session.LastActivity > Expiry) session.Turns.Clear();
            // Cat ngan de khong tang qua muc bo nho va prompt.
            session.Turns.Add(new ChatTurn("user", question.Length > 1000 ? question[..1000] : question));
            session.Turns.Add(new ChatTurn("assistant", answer.Length > 1500 ? answer[..1500] : answer));
            while (session.Turns.Count > MaxTurns) session.Turns.RemoveAt(0);
            session.LastActivity = DateTimeOffset.UtcNow;
        }
        if (_sessions.Count > 1000)
            foreach (var pair in _sessions)
                if (DateTimeOffset.UtcNow - pair.Value.LastActivity > Expiry)
                    _sessions.TryRemove(pair.Key, out _);
    }

    public void Clear(int subscriberId) => _sessions.TryRemove(subscriberId, out _);
}
