async function attachThreadsSignals() {
  const host = document.getElementById("trending-list");
  if (!host) return;
  try {
    const response = await fetch(`./data/threads_signals.json?v=${Date.now()}`, { cache: "no-store" });
    if (!response.ok) return;
    const data = await response.json();
    const byTitle = new Map((data.items || []).map((item) => [String(item.title || "").trim(), item]));
    host.querySelectorAll(".trend-item").forEach((card) => {
      if (card.dataset.threadsSignal === "shown") return;
      const title = card.querySelector("h3")?.textContent?.trim();
      const signal = byTitle.get(title);
      if (!signal || !Number.isFinite(Number(signal.recent_public_posts))) return;
      const line = document.createElement("p");
      line.className = "threads-signal";
      line.textContent = `إشارة Threads العامة: ${signal.recent_public_posts} منشورًا في آخر رصد`;
      card.querySelector("div")?.append(line);
      card.dataset.threadsSignal = "shown";
    });
  } catch (error) {
    console.info("Threads trend signal not available", error);
  }
}
attachThreadsSignals();
const trendHost = document.getElementById("trending-list");
if (trendHost) new MutationObserver(() => attachThreadsSignals()).observe(trendHost, { childList: true, subtree: true });
window.setInterval(attachThreadsSignals, 10 * 60 * 1000);
