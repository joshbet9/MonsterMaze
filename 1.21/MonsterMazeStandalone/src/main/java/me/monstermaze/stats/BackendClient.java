package me.monstermaze.stats;

import me.monstermaze.MonsterMazePlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Direct HTTPS client used by public servers. */
public final class BackendClient {
    private final MonsterMazePlugin plugin;
    private final String baseUrl;
    private final String token;
    private final File pendingDir;

    public BackendClient(MonsterMazePlugin plugin) {
        this.plugin = plugin;
        this.baseUrl = normalize(System.getenv("MM_API_URL"));
        this.token = trim(System.getenv("MM_API_TOKEN"));
        this.pendingDir = new File(plugin.getDataFolder(), "backend-pending");
        if (isEnabled()) {
            plugin.getLogger().info("Monster Maze backend API enabled: " + baseUrl);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() { @Override public void run() { flushPending(); } }, 40L);
        }
    }
    public boolean isEnabled() { return !baseUrl.isEmpty() && !token.isEmpty(); }
    public void submit(final String submissionId, final UUID uuid, final String name, final String mode, final int pattern, final String kit, final int stage, final long elapsedMs, final String platform, final String pluginVersion, final String configHash, final long submittedAt) {
        if (!isEnabled()) return;
        final String payload = buildPayload(submissionId, uuid, name, mode, pattern, kit, stage, elapsedMs, platform, pluginVersion, configHash, submittedAt);
        final File pending = new File(pendingDir, "backend-" + safeFileName(submissionId) + ".json");
        new BukkitRunnable() { @Override public void run() { if (!queuePayload(pending, payload)) return; trySubmitPending(pending); } }.runTaskAsynchronously(plugin);
    }
    public String get(String path) throws Exception {
        if (!isEnabled()) return null;
        String suffix = path == null ? "" : path; if (!suffix.startsWith("/")) suffix = "/" + suffix;
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + suffix).openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(5000); c.setReadTimeout(10000); c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Accept", "application/json");
        try { int status = c.getResponseCode(); InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream(); StringBuilder body = new StringBuilder(); if (stream != null) { BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)); try { String line; while ((line=r.readLine())!=null) body.append(line); } finally { r.close(); } } if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + (body.length()>0 ? " " + body : "")); return body.toString(); } finally { c.disconnect(); }
    }
    private void flushPending() { if (!isEnabled() || !pendingDir.exists()) return; new BukkitRunnable() { @Override public void run() { File[] files = pendingDir.listFiles(); if (files == null) return; for (File f : files) if (f.isFile() && f.getName().startsWith("backend-") && f.getName().endsWith(".json")) trySubmitPending(f); } }.runTaskAsynchronously(plugin); }
    private boolean queuePayload(File pending, String payload) { if (pending.exists()) return true; if (!pendingDir.exists() && !pendingDir.mkdirs()) { plugin.getLogger().warning("Could not create backend retry directory: " + pendingDir); return false; } FileOutputStream out = null; try { out = new FileOutputStream(pending); out.write(payload.getBytes(StandardCharsets.UTF_8)); out.flush(); return true; } catch (IOException e) { plugin.getLogger().warning("Could not queue backend submission: " + e.getMessage()); return false; } finally { if (out != null) try { out.close(); } catch (IOException ignored) {} } }
    private void trySubmitPending(File pending) { String payload; try { payload = readFile(pending); } catch (IOException e) { plugin.getLogger().warning("Could not read backend queue file " + pending.getName() + ": " + e.getMessage()); return; } int attempts=0; long delay=1000L; while (attempts<4) { attempts++; try { post(payload); if (!pending.delete() && pending.exists()) plugin.getLogger().warning("Backend accepted " + pending.getName() + " but it could not be deleted."); plugin.getLogger().info("Run submitted to Monster Maze backend: " + pending.getName()); if (plugin.getLeaderboards() != null) plugin.getLeaderboards().refreshFromBackend(); if (plugin.getChallengeManager() != null) plugin.getChallengeManager().refresh(); return; } catch (Exception e) { if (attempts>=4) { plugin.getLogger().warning("Backend submission failed for " + pending.getName() + " after " + attempts + " attempts: " + e.getMessage()); return; } try { Thread.sleep(delay); } catch (InterruptedException x) { Thread.currentThread().interrupt(); return; } delay*=2L; } } }
    private String readFile(File f) throws IOException { FileInputStream in=new FileInputStream(f); try { byte[] d=new byte[(int)Math.min(Integer.MAX_VALUE,f.length())]; int o=0,r; while(o<d.length&&(r=in.read(d,o,d.length-o))>=0)o+=r; return new String(d,0,o,StandardCharsets.UTF_8); } finally { in.close(); } }
    private void post(String payload) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+"/api/v1/runs").openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(5000); c.setReadTimeout(10000); c.setDoOutput(true); c.setRequestProperty("Authorization","Bearer "+token); c.setRequestProperty("Content-Type","application/json; charset=utf-8"); byte[] b=payload.getBytes(StandardCharsets.UTF_8); OutputStream o=c.getOutputStream(); try{o.write(b);}finally{o.close();} int s=c.getResponseCode(); if(s<200||s>=300){c.disconnect();throw new IllegalStateException("HTTP "+s);} c.disconnect(); }
    private static String buildPayload(String submissionId, UUID uuid, String name, String mode, int pattern, String kit, int stage, long elapsedMs, String platform, String pluginVersion, String configHash, long submittedAt) { StringBuilder sb=new StringBuilder("{"); field(sb,"submissionId",submissionId,true); field(sb,"platform",platform,true); field(sb,"plugin",pluginVersion,true); field(sb,"name",name,true); field(sb,"uuid",uuid.toString(),true); field(sb,"mode",mode,true); number(sb,"pattern",pattern,true); field(sb,"kit",kit,true); number(sb,"stage",stage,true); number(sb,"timeMs",elapsedMs,true); field(sb,"configHash",configHash,true); number(sb,"submittedAt",submittedAt,false); sb.append("}"); return sb.toString(); }
    private static void field(StringBuilder sb,String k,String v,boolean comma){sb.append("\"").append(escape(k)).append("\":\"").append(escape(v)).append("\"");if(comma)sb.append(",");}
    private static void number(StringBuilder sb,String k,long v,boolean comma){sb.append("\"").append(escape(k)).append("\":").append(v);if(comma)sb.append(",");}
    private static String safeFileName(String v){return v.replaceAll("[^A-Za-z0-9._-]","_");}
    private static String normalize(String v){String s=trim(v);while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    private static String trim(String v){return v==null?"":v.trim();}
    private static String escape(String v){if(v==null)return"";return v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
}
