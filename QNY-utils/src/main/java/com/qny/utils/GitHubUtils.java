package com.qny.utils;

import com.alibaba.fastjson.JSON;
import com.mysql.jdbc.util.Base64Decoder;
import com.qny.model.start.common.KimiDto;
import com.qny.model.start.common.KimiMessage;
import com.qny.model.start.dto.ChatCompletionResponse;
import com.qny.model.start.dto.GitHubContent;
import com.qny.model.start.dto.GitHubRepoFile;
import com.qny.model.start.dto.UserDto;
import com.qny.model.start.pojos.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class GitHubUtils {

    // 最大的用户数
    private static int USER_MAX_NUM = 9930000;
    // following的权重
    private static int FOLLOWING_W = 5;
    // followers的权重
    private static int FOLLOWERS_W = 2;
    // 访问github，加速token
    private static String AUTH_GITHUB = "token ";
    // kimi的token 需要自行获取
    private static String AUTH_KIMI = "";
    // kimi接口地址
    private static final String CHAT_COMPLETION_URL = "https://api.moonshot.cn/v1/chat/completions";
    // 线程数
    private static int THREAD_NUM = 20;

    /**
     * 向Kimi发送请求
     * @param json
     * @return
     */
    private static String request_kimi(String json) {

        try {
            URL url = new URL(CHAT_COMPLETION_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

            // 设置请求方法
            conn.setRequestMethod("POST");
            // 设置允许输出
            conn.setDoOutput(true);
            // 设置Content-Type为application/json
            conn.setRequestProperty("Content-Type", "application/json");
            // 设置接受响应
            conn.setDoInput(true);
            // 发送POST请求必须设置
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Authorization", "Bearer " + AUTH_KIMI);

            // 写入JSON请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                // 获取数据
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            } else {
                log.warn("POST请求没有成功：" + responseCode);
                System.out.println("POST请求没有成功：" + responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return null;
    }

    /**
     * 给定地址发送请求
     * @param httpsUrl
     * @return
     */
    private static String request(String httpsUrl) {
        try {
            URL url = new URL(httpsUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Authorization", AUTH_GITHUB);

            conn.connect();
            int responseCode = conn.getResponseCode();
            // 200
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            } else {
                log.warn("GET请求没有成功：" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace(); // 更详细的异常信息
            throw new RuntimeException("Failed", e);
        }

        return null;
    }

    /**
     * 给定login获取用户详细数据
     * @param userName
     * @return
     */
    public static UserDto getUserInfo(String userName) {
        String userUrl = "https://api.github.com/users/" + userName;
        String request = request(userUrl);

        return JSON.parseObject(request, UserDto.class);
    }

    /**
     * 计算粉丝/关注的人中，哪个地址出现次数最多
     * @param user
     * @param locationCount
     * @param w 权重
     */
    private static void processUser(User user, Map<String, Integer> locationCount, Integer w) {
        String userUrl = "https://api.github.com/users/" + user.getLogin();
        String request = request(userUrl);

        if (!StringUtils.isNotBlank(request)) return;;

        // JSON转换成Java类
        UserInfo userInfo = JSON.parseObject(request, UserInfo.class);
        String location = userInfo.getLocation();
        if (StringUtils.isNotBlank(location)) {
            if (location.contains(",")) {
                String[] locations = location.split(",");

                for (int i = 0; i < locations.length; i ++) {
                    // 计算地区出现次数*权重，越在后面越重要
                    locationCount.put(locations[i].trim(), locationCount.getOrDefault(locations[i].trim(), 0) + w);
                }
            }
            else {
                String key = location.trim();
                locationCount.put(key, locationCount.getOrDefault(key, 0) + w);
            }
        }
    }

    /**
     * 给名字，返回最有可能的国家（轮询用户社交关系，未采用）
     * @param userName
     * @return
     */
    public static String getUserLocations(String userName) {
        String httpsUrlFollowers = "https://api.github.com/users/" + userName + "/followers";
        String httpsUrlFollowing = "https://api.github.com/users/" + userName + "/following";
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);
        // 线程池，个数可选
        Callable<List<User>> followersTask = () -> JSON.parseArray(request(httpsUrlFollowers), User.class);
        Callable<List<User>> followingTask = () -> JSON.parseArray(request(httpsUrlFollowing), User.class);

        try {
            Future<List<User>> followersFuture = executor.submit(followersTask);
            Future<List<User>> followingFuture = executor.submit(followingTask);

            List<User> followers = followersFuture.get();
            List<User> following = followingFuture.get();

            Map<String, Integer> locationCount = new HashMap<>();

            if (followers != null) {
                for (User user : followers) {
                    executor.submit((Callable<Void>) () -> {
                        processUser(user, locationCount, FOLLOWERS_W);
                        return null;
                    });
                }

            }

            if (following != null) {
                for (User user : following) {
                    executor.submit((Callable<Void>) () -> {
                        // 关注的人比粉丝更重要，所以权重更高
                        processUser(user, locationCount, FOLLOWING_W);
                        return null;
                    });
                }
            }

            executor.shutdown();
            if (!executor.awaitTermination(300, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            String mostFrequentLocation = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : locationCount.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    mostFrequentLocation = entry.getKey();
                }
            }

            // 数据不可靠
            if (maxCount < 20) return "N/A";

            if (mostFrequentLocation != null) {
                log.info("最有可能的地区/国家是：" + mostFrequentLocation + "  权重：" + maxCount + " 分.");
            } else {
                log.info("未找到最可能的地点");
            }

            KimiDto kimiDto = new KimiDto();
            kimiDto.setModel("moonshot-v1-8k");

            List<KimiMessage> list = new ArrayList<>();
            KimiMessage kimiMessage = new KimiMessage();
            kimiMessage.setRole("user");
            // 构建问题
            kimiMessage.setContent(mostFrequentLocation + " 这是哪个国家，只返回国家名字，不返回其他字");
            list.add(kimiMessage);

            kimiDto.setMessages(list);

            // 询问类chatgpt应用 该用户属于哪个国家
            String s = request_kimi(JSON.toJSONString(kimiDto));

            ChatCompletionResponse chatCompletionResponse = JSON.parseObject(s, ChatCompletionResponse.class);

            if (chatCompletionResponse == null) return mostFrequentLocation;

            for (ChatCompletionResponse.Choice choice : chatCompletionResponse.getChoices()) {
                mostFrequentLocation = choice.getMessage().getContent();
            }

            // 为空或长度大于10（说明传入了一个非国家的数据） 数据不可靠
            if (!StringUtils.isNotBlank(mostFrequentLocation) || mostFrequentLocation.length() > 10) return "N/A";

            return mostFrequentLocation;
        } catch (Exception e) {
            e.printStackTrace(); // 更详细的异常信息
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    /**
     * 通过用户的工作时间（从中得到时区），以及用户的社交关系，利用类chatGPT软件推理
     * @param userName
     * @return
     */
    public static String getUserLocations1(String userName) {
        String httpsUrlFollowers = "https://api.github.com/users/" + userName + "/followers";
        String httpsUrlFollowing = "https://api.github.com/users/" + userName + "/following";
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);
        // 线程池，个数可选
        Callable<List<User>> followersTask = () -> JSON.parseArray(request(httpsUrlFollowers), User.class);
        Callable<List<User>> followingTask = () -> JSON.parseArray(request(httpsUrlFollowing), User.class);

        try {
            Future<List<User>> followersFuture = executor.submit(followersTask);
            Future<List<User>> followingFuture = executor.submit(followingTask);

            List<User> followers = followersFuture.get();
            List<User> following = followingFuture.get();

            Map<String, Integer> locationCount1 = new HashMap<>();
            Map<String, Integer> locationCount2 = new HashMap<>();

            if (followers != null) {
                for (User user : followers) {
                    executor.submit((Callable<Void>) () -> {
                        processUser(user, locationCount1, 1);
                        return null;
                    });
                }

            }

            if (following != null) {
                for (User user : following) {
                    executor.submit((Callable<Void>) () -> {
                        processUser(user, locationCount2, 1);
                        return null;
                    });
                }
            }

            executor.shutdown();
            if (!executor.awaitTermination(300, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            String json = "https://api.github.com/users/" + userName + "/events";
            String request = request(json);

            List<Events> events = JSON.parseArray(request, Events.class);

            List<String> dateList = new ArrayList<>();
            if (events != null) {
                for (Events event : events) {
                    String createdAt = event.getCreatedAt();
                    String updatedAt = event.getUpdatedAt();
                    if (StringUtils.isNotBlank(createdAt)) dateList.add(createdAt);
                    if (StringUtils.isNotBlank(updatedAt)) dateList.add(updatedAt);
                }
            }

            // 置信度太低
            if (locationCount1.size() < 5 && locationCount2.size() < 3) return "N/A";

            UserDto userDto = getUserInfo(userName);

            KimiDto kimiDto = new KimiDto();
            kimiDto.setModel("moonshot-v1-auto");

            List<KimiMessage> list = new ArrayList<>();

            // 构建问题
            KimiMessage kimiMessage1 = new KimiMessage();
            kimiMessage1.setRole("user");
            kimiMessage1.setContent("用户的工作时间记录：" + dateList);
            list.add(kimiMessage1);

            KimiMessage kimiMessage2 = new KimiMessage();
            kimiMessage2.setRole("user");
            kimiMessage2.setContent("用户关注的人的居住地数量：" + locationCount2);
            list.add(kimiMessage2);

            KimiMessage kimiMessage3 = new KimiMessage();
            kimiMessage3.setRole("user");
            kimiMessage3.setContent("用户粉丝的居住地数量：" + locationCount1);
            list.add(kimiMessage3);

            if (userDto != null) {
                if (StringUtils.isNotBlank(userDto.getName())) {
                    KimiMessage kimiMessage4 = new KimiMessage();
                    kimiMessage4.setRole("user");
                    kimiMessage4.setContent("用户的github名字：" + userDto.getName());
                    list.add(kimiMessage4);
                }
            }

            KimiMessage kimiMessage6 = new KimiMessage();
            kimiMessage6.setRole("user");
            kimiMessage6.setContent("请根据用户的工作时间（从中获得时区），用户的社交关系，用户的名字（如果存在）推断该github用户最有可能的国籍，只需要显示推断理由和结果，若置信度过低则返回N/A");
            list.add(kimiMessage6);

            kimiDto.setMessages(list);

            // 询问类chatgpt应用 该用户属于哪个国家
            String s = request_kimi(JSON.toJSONString(kimiDto));

            String mostFrequentLocation = "";

            ChatCompletionResponse chatCompletionResponse = JSON.parseObject(s, ChatCompletionResponse.class);

            if (chatCompletionResponse == null) return "N/A";

            for (ChatCompletionResponse.Choice choice : chatCompletionResponse.getChoices()) {
                mostFrequentLocation = choice.getMessage().getContent();
            }

            return mostFrequentLocation;
        } catch (Exception e) {
            e.printStackTrace(); // 更详细的异常信息
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取给定数量的用户
     * @param nums
     * @return
     */
    public static List<User> getUser(int nums) {
        int pageSize;
        int page;
        if (nums < 100) {
            pageSize = nums;
            page = 1;
        } else {
            page = nums / 100;
            pageSize = 100;
        }

        List<User> userList = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);

        try {
            for (int i = 0; i < page; i++) {
                Random random = new Random();
                // 获取随机起点
                int randomInt = random.nextInt(USER_MAX_NUM);

                String url = "https://api.github.com/users?repos>0&since=" + randomInt + "&per_page=" + pageSize;
                Callable<List<User>> task = () -> JSON.parseArray(request(url), User.class);
                Future<List<User>> future = executor.submit(task);
                List<User> list = future.get();
                if (list != null) userList.addAll(future.get());
            }
        } catch (Exception e) {
            e.printStackTrace(); // 更详细的异常信息
            throw new RuntimeException("Failed to fetch users", e);
        } finally {
            executor.shutdown();
        }

        return userList;
    }

    // 技术评估的分数权重
    private static int S_STAR_W = 2;
    private static int S_FORK_W = 20;
    private static int S_ISSUE_W = 10;
    private static int S_WATCHER_W = 1;
    private static int S_FOLLWERS_W = 5;
    private static int S_REPO_COUNT_W = 10;

    /**
     * 技术得分
     * @param userName
     * @return
     */
    public static Integer getScore(String userName) {
        // 获取该用户参与的全部仓库
        String url = "https://api.github.com/users/" + userName + "/repos?type=all";
        String request = request(url);

        UserDto userInfo = getUserInfo(userName);

        Integer score = 0;

        // 粉丝
        if (userInfo != null) score += userInfo.getFollowers() * S_FOLLWERS_W;

        List<Repos> repos = JSON.parseArray(request, Repos.class);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);

        // 仓库数量
        if (repos != null && !repos.isEmpty()) score += repos.size() * S_REPO_COUNT_W;

        try {
            // 计算用的仓库得分
            if (repos != null) {
                for (Repos repo : repos) {
                    Callable<Integer> task = () -> getRepoScore(repo, userName);

                    Future<Integer> future = executor.submit(task);
                    score += future.get();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch users", e);
        } finally {

            executor.shutdown();
            try {
                // 超过5min强行停止
                if (!executor.awaitTermination(300, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return score;
    }

    /**
     * 给定仓库和用户名，获得分值
     * @param repo
     * @param userName
     * @return
     */
    private static int getRepoScore(Repos repo, String userName) {
        int stargazersCount = repo.getStargazers_count();
        int forksCount = repo.getForks_count();
        int openIssuesCount = repo.getOpen_issues_count();
        int watchersCount = repo.getWatchers_count();
        double time = repo.getUpdatedAt().getTime() / 1e12;

        String contributorsUrl = repo.getContributors_url();

        // 获取贡献比例
        String r = request(contributorsUrl);

        if (!StringUtils.isNotBlank(r)) return 0;

        int my_w = 0;
        int sum_w = 0;
        for (Contributors contributors : JSON.parseArray(r, Contributors.class)) {
            if (StringUtils.equals(contributors.getLogin(), userName)) my_w = contributors.getContributions();
            sum_w += contributors.getContributions();
        }

        if (sum_w == 0 || my_w == 0) return 0;
        // 该用户在这个仓库中的贡献占比
        int w = my_w / sum_w;

        // 分值计算公式
        return (int) ((stargazersCount * S_STAR_W + forksCount * S_FORK_W + openIssuesCount * S_ISSUE_W + watchersCount * S_WATCHER_W) * time * w);
    }

    /**
     * 获取指定用户名的全部仓库
     * @param userName
     * @return
     */
    public static List<Repos> getRepo(String userName) {
        String url = "https://api.github.com/users/" + userName + "/repos?type=all";
        String request = request(url);

        List<Repos> repos = JSON.parseArray(request, Repos.class);

        return repos;
    }

    /**
     * 通过AI阅读该用户start最高的五个项目的readme之后，得到对该用户的技术总结
     * @param userName
     * @return
     */
    public static String getKimiEvaluate(String userName) {
        // 获得该用户的全部仓库
        List<Repos> repos = getRepo(userName);

        if (repos == null) return null;

        UserDto userInfo = getUserInfo(userName);

        // 取出start值最高的三个项目
        List<Repos> collect = repos.stream().sorted(Comparator.comparingInt(Repos::getStargazers_count).reversed())
                .limit(3)
                .collect(Collectors.toList());

        KimiDto kimiDto = new KimiDto();
        kimiDto.setModel("moonshot-v1-auto");

        List<KimiMessage> kimiMessageList = new ArrayList<>();

        if (userInfo != null && StringUtils.isNotBlank(userInfo.getBio())) {
            KimiMessage message = new KimiMessage();
            message.setRole("user");
            message.setContent("用户介绍：" + userInfo.getBio() + "，需要将此介绍也输出出来");
            kimiMessageList.add(message);
        }

        for (Repos repo : collect) {
            String repoName = repo.getName();
            String url = "https://api.github.com/repos/" + userName + "/" + repoName + "/contents/";
            String request = request(url);

            List<GitHubContent> gitHubContents = JSON.parseArray(request, GitHubContent.class);

            if (gitHubContents == null) continue;

            url = "";

            for (GitHubContent gitHubContent : gitHubContents) {
                String gitHubContentUrl = gitHubContent.getUrl();
                if (gitHubContentUrl.contains("README") || gitHubContentUrl.contains("readme")) url = gitHubContentUrl;
            }

            if (!StringUtils.isNotBlank(url)) continue;

            request = request(url);

            GitHubRepoFile gitHubRepoFile = JSON.parseObject(request, GitHubRepoFile.class);

            if (gitHubRepoFile == null) continue;

            // 获取readme内容
            String content = gitHubRepoFile.getContent();

            if (!StringUtils.isNotBlank(content)) continue;

            // base64解码
            String s = base64(content);

            KimiMessage kimiMessage = new KimiMessage();
            kimiMessage.setContent(s);
            kimiMessage.setRole("user");
            kimiMessageList.add(kimiMessage);
        }

        KimiMessage kimiMessage = new KimiMessage();
        kimiMessage.setRole("user");
        kimiMessage.setContent("请根据用户介绍（如果存在）和他的一些作品的readme文件，简要介绍作者的技术特点");
        kimiMessageList.add(kimiMessage);

        kimiDto.setMessages(kimiMessageList);

        String jsonString = JSON.toJSONString(kimiDto);

        return request_kimi(jsonString);
    }

    /**
     * base64解码
     * @param base64EncodedString
     * @return
     */
    public static String base64(String base64EncodedString) {
        // 清理字符串，移除任何非法Base64字符
        String cleanedString = base64EncodedString
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "");

        try {
            // 尝试使用标准Base64解码器解码
            byte[] decodedBytes = Base64.getDecoder().decode(cleanedString);
            return new String(decodedBytes);
        } catch (IllegalArgumentException e) {
            // 如果标准解码器失败，尝试URL安全的解码器
            try {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(cleanedString);
                return new String(decodedBytes);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

//    public static void main(String[] args) {

//        System.out.println(System.currentTimeMillis());
//        String s = "1234----1111111111";
//
//        for (String string : s.split("----")) {
//            System.out.println(string);
//        }


//        GitHubUtils gitHubUtils = new GitHubUtils();
//
//        String s = getUserLocations1("zebslc");
//
//        System.out.println(s);
//
//        s.split()

//
//        String locationCount = gitHubUtils.getUserLocations("ruanyl");
//        System.out.println(locationCount);

//        gitHubUtils.getUser(200);
//        System.out.println(gitHubUtils.getScore("tpope"));
//        String s1 = "IyBmdWdpdGl2ZS52aW0KCkZ1Z2l0aXZlIGlzIHRoZSBwcmVtaWVyIFZpbSBw\\nbHVnaW4gZm9yIEdpdC4gIE9yIG1heWJlIGl0J3MgdGhlIHByZW1pZXIgR2l0\\nCnBsdWdpbiBmb3IgVmltPyAgRWl0aGVyIHdheSwgaXQncyAic28gYXdlc29t\\nZSwgaXQgc2hvdWxkIGJlIGlsbGVnYWwiLiAgVGhhdCdzCndoeSBpdCdzIGNh\\nbGxlZCBGdWdpdGl2ZS4KClRoZSBjcm93biBqZXdlbCBvZiBGdWdpdGl2ZSBp\\ncyBgOkdpdGAgKG9yIGp1c3QgYDpHYCksIHdoaWNoIGNhbGxzIGFueQphcmJp\\ndHJhcnkgR2l0IGNvbW1hbmQuICBJZiB5b3Uga25vdyBob3cgdG8gdXNlIEdp\\ndCBhdCB0aGUgY29tbWFuZCBsaW5lLCB5b3UKa25vdyBob3cgdG8gdXNlIGA6\\nR2l0YC4gIEl0J3MgdmFndWVseSBha2luIHRvIGA6IWdpdGAgYnV0IHdpdGgg\\nbnVtZXJvdXMKaW1wcm92ZW1lbnRzOgoKKiBUaGUgZGVmYXVsdCBiZWhhdmlv\\nciBpcyB0byBkaXJlY3RseSBlY2hvIHRoZSBjb21tYW5kJ3Mgb3V0cHV0LiAg\\nUXVpZXQKICBjb21tYW5kcyBsaWtlIGA6R2l0IGFkZGAgYXZvaWQgdGhlIGRy\\nZWFkZWQgIlByZXNzIEVOVEVSIG9yIHR5cGUgY29tbWFuZCB0bwogIGNvbnRp\\nbnVlIiBwcm9tcHQuCiogYDpHaXQgY29tbWl0YCwgYDpHaXQgcmViYXNlIC1p\\nYCwgYW5kIG90aGVyIGNvbW1hbmRzIHRoYXQgaW52b2tlIGFuIGVkaXRvciBk\\nbwogIHRoZWlyIGVkaXRpbmcgaW4gdGhlIGN1cnJlbnQgVmltIGluc3RhbmNl\\nLgoqIGA6R2l0IGRpZmZgLCBgOkdpdCBsb2dgLCBhbmQgb3RoZXIgdmVyYm9z\\nZSwgcGFnaW5hdGVkIGNvbW1hbmRzIGhhdmUgdGhlaXIKICBvdXRwdXQgbG9h\\nZGVkIGludG8gYSB0ZW1wb3JhcnkgYnVmZmVyLiAgRm9yY2UgdGhpcyBiZWhh\\ndmlvciBmb3IgYW55IGNvbW1hbmQKICB3aXRoIGA6R2l0IC0tcGFnaW5hdGVg\\nIG9yIGA6R2l0IC1wYC4KKiBgOkdpdCBibGFtZWAgdXNlcyBhIHRlbXBvcmFy\\neSBidWZmZXIgd2l0aCBtYXBzIGZvciBhZGRpdGlvbmFsIHRyaWFnZS4gIFBy\\nZXNzCiAgZW50ZXIgb24gYSBsaW5lIHRvIHZpZXcgdGhlIGNvbW1pdCB3aGVy\\nZSB0aGUgbGluZSBjaGFuZ2VkLCBvciBgZz9gIHRvIHNlZQogIG90aGVyIGF2\\nYWlsYWJsZSBtYXBzLiAgT21pdCB0aGUgZmlsZW5hbWUgYXJndW1lbnQgYW5k\\nIHRoZSBjdXJyZW50bHkgZWRpdGVkCiAgZmlsZSB3aWxsIGJlIGJsYW1lZCBp\\nbiBhIHZlcnRpY2FsLCBzY3JvbGwtYm91bmQgc3BsaXQuCiogYDpHaXQgbWVy\\nZ2V0b29sYCBhbmQgYDpHaXQgZGlmZnRvb2xgIGxvYWQgdGhlaXIgY2hhbmdl\\nc2V0cyBpbnRvIHRoZSBxdWlja2ZpeAogIGxpc3QuCiogQ2FsbGVkIHdpdGgg\\nbm8gYXJndW1lbnRzLCBgOkdpdGAgb3BlbnMgYSBzdW1tYXJ5IHdpbmRvdyB3\\naXRoIGRpcnR5IGZpbGVzIGFuZAogIHVucHVzaGVkIGFuZCB1bnB1bGxlZCBj\\nb21taXRzLiAgUHJlc3MgYGc/YCB0byBicmluZyB1cCBhIGxpc3Qgb2YgbWFw\\ncyBmb3IKICBudW1lcm91cyBvcGVyYXRpb25zIGluY2x1ZGluZyBkaWZmaW5n\\nLCBzdGFnaW5nLCBjb21taXR0aW5nLCByZWJhc2luZywgYW5kCiAgc3Rhc2hp\\nbmcuICAoVGhpcyBpcyB0aGUgc3VjY2Vzc29yIHRvIHRoZSBvbGQgYDpHc3Rh\\ndHVzYC4pCiogVGhpcyBjb21tYW5kIChhbG9uZyB3aXRoIGFsbCBvdGhlciBj\\nb21tYW5kcykgYWx3YXlzIHVzZXMgdGhlIGN1cnJlbnQKICBidWZmZXIncyBy\\nZXBvc2l0b3J5LCBzbyB5b3UgZG9uJ3QgbmVlZCB0byB3b3JyeSBhYm91dCB0\\naGUgY3VycmVudCB3b3JraW5nCiAgZGlyZWN0b3J5LgoKQWRkaXRpb25hbCBj\\nb21tYW5kcyBhcmUgcHJvdmlkZWQgZm9yIGhpZ2hlciBsZXZlbCBvcGVyYXRp\\nb25zOgoKKiBWaWV3IGFueSBibG9iLCB0cmVlLCBjb21taXQsIG9yIHRhZyBp\\nbiB0aGUgcmVwb3NpdG9yeSB3aXRoIGA6R2VkaXRgIChhbmQKICBgOkdzcGxp\\ndGAsIGV0Yy4pLiAgRm9yIGV4YW1wbGUsIGA6R2VkaXQgSEVBRH4zOiVgIGxv\\nYWRzIHRoZSBjdXJyZW50IGZpbGUgYXMKICBpdCBleGlzdGVkIDMgY29tbWl0\\ncyBhZ28uCiogYDpHZGlmZnNwbGl0YCAob3IgYDpHdmRpZmZzcGxpdGApIGJy\\naW5ncyB1cCB0aGUgc3RhZ2VkIHZlcnNpb24gb2YgdGhlIGZpbGUKICBzaWRl\\nIGJ5IHNpZGUgd2l0aCB0aGUgd29ya2luZyB0cmVlIHZlcnNpb24uICBVc2Ug\\nVmltJ3MgZGlmZiBoYW5kbGluZwogIGNhcGFiaWxpdGllcyB0byBhcHBseSBj\\naGFuZ2VzIHRvIHRoZSBzdGFnZWQgdmVyc2lvbiwgYW5kIHdyaXRlIHRoYXQg\\nYnVmZmVyCiAgdG8gc3RhZ2UgdGhlIGNoYW5nZXMuICBZb3UgY2FuIGFsc28g\\nZ2l2ZSBhbiBhcmJpdHJhcnkgYDpHZWRpdGAgYXJndW1lbnQgdG8KICBkaWZm\\nIGFnYWluc3Qgb2xkZXIgdmVyc2lvbnMgb2YgdGhlIGZpbGUuCiogYDpHcmVh\\nZGAgaXMgYSB2YXJpYW50IG9mIGBnaXQgY2hlY2tvdXQgLS0gZmlsZW5hbWVg\\nIHRoYXQgb3BlcmF0ZXMgb24gdGhlCiAgYnVmZmVyIHJhdGhlciB0aGFuIHRo\\nZSBmaWxlIGl0c2VsZi4gIFRoaXMgbWVhbnMgeW91IGNhbiB1c2UgYHVgIHRv\\nIHVuZG8gaXQKICBhbmQgeW91IG5ldmVyIGdldCBhbnkgd2FybmluZ3MgYWJv\\ndXQgdGhlIGZpbGUgY2hhbmdpbmcgb3V0c2lkZSBWaW0uCiogYDpHd3JpdGVg\\nIHdyaXRlcyB0byBib3RoIHRoZSB3b3JrIHRyZWUgYW5kIGluZGV4IHZlcnNp\\nb25zIG9mIGEgZmlsZSwgbWFraW5nCiAgaXQgbGlrZSBgZ2l0IGFkZGAgd2hl\\nbiBjYWxsZWQgZnJvbSBhIHdvcmsgdHJlZSBmaWxlIGFuZCBsaWtlIGBnaXQg\\nY2hlY2tvdXRgCiAgd2hlbiBjYWxsZWQgZnJvbSB0aGUgaW5kZXggb3IgYSBi\\nbG9iIGluIGhpc3RvcnkuCiogYDpHZ3JlcGAgaXMgYDpncmVwYCBmb3IgYGdp\\ndCBncmVwYC4gIGA6R2xncmVwYCBpcyBgOmxncmVwYCBmb3IgdGhlIHNhbWUu\\nCiogYDpHTW92ZWAgZG9lcyBhIGBnaXQgbXZgIG9uIHRoZSBjdXJyZW50IGZp\\nbGUgYW5kIGNoYW5nZXMgdGhlIGJ1ZmZlciBuYW1lIHRvCiAgbWF0Y2guICBg\\nOkdSZW5hbWVgIGRvZXMgdGhlIHNhbWUgd2l0aCBhIGRlc3RpbmF0aW9uIGZp\\nbGVuYW1lIHJlbGF0aXZlIHRvIHRoZQogIGN1cnJlbnQgZmlsZSdzIGRpcmVj\\ndG9yeS4KKiBgOkdEZWxldGVgIGRvZXMgYSBgZ2l0IHJtYCBvbiB0aGUgY3Vy\\ncmVudCBmaWxlIGFuZCBzaW11bHRhbmVvdXNseSBkZWxldGVzCiAgdGhlIGJ1\\nZmZlci4gIGA6R1JlbW92ZWAgZG9lcyB0aGUgc2FtZSBidXQgbGVhdmVzIHRo\\nZSAobm93IGVtcHR5KSBidWZmZXIKICBvcGVuLgoqIGA6R0Jyb3dzZWAgdG8g\\nb3BlbiB0aGUgY3VycmVudCBmaWxlIG9uIHRoZSB3ZWIgZnJvbnQtZW5kIG9m\\nIHlvdXIgZmF2b3JpdGUKICBob3N0aW5nIHByb3ZpZGVyLCB3aXRoIG9wdGlv\\nbmFsIGxpbmUgcmFuZ2UgKHRyeSBpdCBpbiB2aXN1YWwgbW9kZSkuICBQbHVn\\naW5zCiAgYXJlIGF2YWlsYWJsZSBmb3IgcG9wdWxhciBwcm92aWRlcnMgc3Vj\\naCBhcyBbR2l0SHViXVtyaHViYXJiLnZpbV0sCiAgW0dpdExhYl1bZnVnaXRp\\ndmUtZ2l0bGFiLnZpbV0sIFtCaXRidWNrZXRdW2Z1Yml0aXZlLnZpbV0sCiAg\\nW0dpdGVlXVtmdWdpdGl2ZS1naXRlZS52aW1dLCBbUGFndXJlXVtwYWd1cmVd\\nLAogIFtQaGFicmljYXRvcl1bdmltLXBoYWJyaWNhdG9yXSwgW0F6dXJlIERl\\ndk9wc11bZnVnaXRpdmUtYXp1cmUtZGV2b3BzLnZpbV0sCiAgYW5kIFtzb3Vy\\nY2VodXRdW3NyaHQudmltXS4KCltyaHViYXJiLnZpbV06IGh0dHBzOi8vZ2l0\\naHViLmNvbS90cG9wZS92aW0tcmh1YmFyYgpbZnVnaXRpdmUtZ2l0bGFiLnZp\\nbV06IGh0dHBzOi8vZ2l0aHViLmNvbS9zaHVtcGhyZXkvZnVnaXRpdmUtZ2l0\\nbGFiLnZpbQpbZnViaXRpdmUudmltXTogaHR0cHM6Ly9naXRodWIuY29tL3Rv\\nbW1jZG8vdmltLWZ1Yml0aXZlCltmdWdpdGl2ZS1naXRlZS52aW1dOiBodHRw\\nczovL2dpdGh1Yi5jb20vbGludXhzdXJlbi9mdWdpdGl2ZS1naXRlZS52aW0K\\nW3BhZ3VyZV06IGh0dHBzOi8vZ2l0aHViLmNvbS9Gcm9zdHlYL3ZpbS1mdWdp\\ndGl2ZS1wYWd1cmUKW3ZpbS1waGFicmljYXRvcl06IGh0dHBzOi8vZ2l0aHVi\\nLmNvbS9qcGFyaXNlL3ZpbS1waGFicmljYXRvcgpbZnVnaXRpdmUtYXp1cmUt\\nZGV2b3BzLnZpbV06IGh0dHBzOi8vZ2l0aHViLmNvbS9jZWRhcmJhdW0vZnVn\\naXRpdmUtYXp1cmUtZGV2b3BzLnZpbQpbc3JodC52aW1dOiBodHRwczovL2dp\\ndC5zci5odC9+d2lsbGR1cmFuZC9zcmh0LnZpbQoKQWRkIGAle0Z1Z2l0aXZl\\nU3RhdHVzbGluZSgpfWAgdG8gYCdzdGF0dXNsaW5lJ2AgdG8gZ2V0IGFuIGlu\\nZGljYXRvcgp3aXRoIHRoZSBjdXJyZW50IGJyYW5jaCBpbiB5b3VyIHN0YXR1\\nc2xpbmUuCgpGb3IgbW9yZSBpbmZvcm1hdGlvbiwgc2VlIGA6aGVscCBmdWdp\\ndGl2ZWAuCgojIyBTY3JlZW5jYXN0cwoKKiBbQSBjb21wbGVtZW50IHRvIGNv\\nbW1hbmQgbGluZSBnaXRdKGh0dHA6Ly92aW1jYXN0cy5vcmcvZS8zMSkKKiBb\\nV29ya2luZyB3aXRoIHRoZSBnaXQgaW5kZXhdKGh0dHA6Ly92aW1jYXN0cy5v\\ncmcvZS8zMikKKiBbUmVzb2x2aW5nIG1lcmdlIGNvbmZsaWN0cyB3aXRoIHZp\\nbWRpZmZdKGh0dHA6Ly92aW1jYXN0cy5vcmcvZS8zMykKKiBbQnJvd3Npbmcg\\ndGhlIGdpdCBvYmplY3QgZGF0YWJhc2VdKGh0dHA6Ly92aW1jYXN0cy5vcmcv\\nZS8zNCkKKiBbRXhwbG9yaW5nIHRoZSBoaXN0b3J5IG9mIGEgZ2l0IHJlcG9z\\naXRvcnldKGh0dHA6Ly92aW1jYXN0cy5vcmcvZS8zNSkKCiMjIEluc3RhbGxh\\ndGlvbgoKSW5zdGFsbCB1c2luZyB5b3VyIGZhdm9yaXRlIHBhY2thZ2UgbWFu\\nYWdlciwgb3IgdXNlIFZpbSdzIGJ1aWx0LWluIHBhY2thZ2UKc3VwcG9ydDoK\\nCiAgICBta2RpciAtcCB+Ly52aW0vcGFjay90cG9wZS9zdGFydAogICAgY2Qg\\nfi8udmltL3BhY2svdHBvcGUvc3RhcnQKICAgIGdpdCBjbG9uZSBodHRwczov\\nL3Rwb3BlLmlvL3ZpbS9mdWdpdGl2ZS5naXQKICAgIHZpbSAtdSBOT05FIC1j\\nICJoZWxwdGFncyBmdWdpdGl2ZS9kb2MiIC1jIHEKCiMjIEZBUQoKPiBXaGF0\\nIGhhcHBlbmVkIHRvIHRoZSBkaXNwYXRjaC52aW0gYmFja2VkIGFzeW5jaHJv\\nbm91cyBgOkdwdXNoYCBhbmQKPiBgOkdmZXRjaGA/CgpUaGlzIGJlaGF2aW9y\\nIHdhcyBkaXZpc2l2ZSwgY29uZnVzaW5nLCBhbmQgY29tcGxpY2F0ZWQgaW5w\\ndXR0aW5nIHBhc3N3b3Jkcywgc28KaXQgd2FzIHJlbW92ZWQuICBVc2UgYDpH\\naXQhIHB1c2hgIHRvIHVzZSBGdWdpdGl2ZSdzIG93biBhc3luY2hyb25vdXMK\\nZXhlY3V0aW9uLCBvciByZXRyb2FjdGl2ZWx5IG1ha2UgYDpHaXQgcHVzaGAg\\nYXN5bmNocm9ub3VzIGJ5IHByZXNzaW5nCmBDVFJMLURgLgoKPiBXaHkgYW0g\\nSSBnZXR0aW5nIGBjb3JlLndvcmt0cmVlIGlzIHJlcXVpcmVkIHdoZW4gdXNp\\nbmcgYW4gZXh0ZXJuYWwgR2l0IGRpcmA/CgpHaXQgZ2VuZXJhbGx5IHNldHMg\\nYGNvcmUud29ya3RyZWVgIGZvciB5b3UgYXV0b21hdGljYWxseSB3aGVuIG5l\\nY2Vzc2FyeSwgYnV0CmlmIHlvdSdyZSBkb2luZyBzb21ldGhpbmcgd2VpcmQs\\nIG9yIHVzaW5nIGEgdGhpcmQtcGFydHkgdG9vbCB0aGF0IGRvZXMKc29tZXRo\\naW5nIHdlaXJkLCB5b3UgbWF5IG5lZWQgdG8gc2V0IGl0IG1hbnVhbGx5OgoK\\nICAgIGdpdCBjb25maWcgY29yZS53b3JrdHJlZSAiJFBXRCIKClRoaXMgbWF5\\nIGJlIG5lY2Vzc2FyeSBldmVuIHdoZW4gc2ltcGxlIGBnaXRgIGNvbW1hbmRz\\nIHNlZW0gdG8gd29yayBmaW5lCndpdGhvdXQgaXQuCgo+IFNvIEkgaGF2ZSBh\\nIHN5bWxpbmsgYW5kLi4uCgpTdG9wLiAgSnVzdCBzdG9wLiAgSWYgR2l0IHdv\\nbid0IGRlYWwgd2l0aCB5b3VyIHN5bWxpbmssIHRoZW4gRnVnaXRpdmUgd29u\\nJ3QKZWl0aGVyLiAgQ29uc2lkZXIgdXNpbmcgYSBbcGx1Z2luIHRoYXQgcmVz\\nb2x2ZXMKc3ltbGlua3NdKGh0dHBzOi8vZ2l0aHViLmNvbS9heW1lcmljYmVh\\ndW1ldC9zeW1saW5rLnZpbSksIG9yIGV2ZW4gYmV0dGVyLAp1c2luZyBmZXdl\\nciBzeW1saW5rcy4KCiMjIFNlbGYtUHJvbW90aW9uCgpMaWtlIGZ1Z2l0aXZl\\nLnZpbT8gRm9sbG93IHRoZSByZXBvc2l0b3J5IG9uCltHaXRIdWJdKGh0dHBz\\nOi8vZ2l0aHViLmNvbS90cG9wZS92aW0tZnVnaXRpdmUpIGFuZCB2b3RlIGZv\\nciBpdCBvbgpbdmltLm9yZ10oaHR0cDovL3d3dy52aW0ub3JnL3NjcmlwdHMv\\nc2NyaXB0LnBocD9zY3JpcHRfaWQ9Mjk3NSkuICBBbmQgaWYKeW91J3JlIGZl\\nZWxpbmcgZXNwZWNpYWxseSBjaGFyaXRhYmxlLCBmb2xsb3cgW3Rwb3BlXSho\\ndHRwOi8vdHBvLnBlLykgb24KW1R3aXR0ZXJdKGh0dHA6Ly90d2l0dGVyLmNv\\nbS90cG9wZSkgYW5kCltHaXRIdWJdKGh0dHBzOi8vZ2l0aHViLmNvbS90cG9w\\nZSkuCgojIyBMaWNlbnNlCgpDb3B5cmlnaHQgKGMpIFRpbSBQb3BlLiAgRGlz\\ndHJpYnV0ZWQgdW5kZXIgdGhlIHNhbWUgdGVybXMgYXMgVmltIGl0c2VsZi4K\\nU2VlIGA6aGVscCBsaWNlbnNlYC4K\\n";
//        String s2 = "IyBjb21tZW50YXJ5LnZpbQoKQ29tbWVudCBzdHVmZiBvdXQuICBVc2UgYGdj\\nY2AgdG8gY29tbWVudCBvdXQgYSBsaW5lICh0YWtlcyBhIGNvdW50KSwKYGdj\\nYCB0byBjb21tZW50IG91dCB0aGUgdGFyZ2V0IG9mIGEgbW90aW9uIChmb3Ig\\nZXhhbXBsZSwgYGdjYXBgIHRvCmNvbW1lbnQgb3V0IGEgcGFyYWdyYXBoKSwg\\nYGdjYCBpbiB2aXN1YWwgbW9kZSB0byBjb21tZW50IG91dCB0aGUgc2VsZWN0\\naW9uLAphbmQgYGdjYCBpbiBvcGVyYXRvciBwZW5kaW5nIG1vZGUgdG8gdGFy\\nZ2V0IGEgY29tbWVudC4gIFlvdSBjYW4gYWxzbyB1c2UKaXQgYXMgYSBjb21t\\nYW5kLCBlaXRoZXIgd2l0aCBhIHJhbmdlIGxpa2UgYDo3LDE3Q29tbWVudGFy\\neWAsIG9yIGFzIHBhcnQgb2YgYQpgOmdsb2JhbGAgaW52b2NhdGlvbiBsaWtl\\nIHdpdGggYDpnL1RPRE8vQ29tbWVudGFyeWAuIFRoYXQncyBpdC4KCkkgd3Jv\\ndGUgdGhpcyBiZWNhdXNlIDUgeWVhcnMgYWZ0ZXIgVmltIGFkZGVkIHN1cHBv\\ncnQgZm9yIG1hcHBpbmcgYW4Kb3BlcmF0b3IsIEkgc3RpbGwgY291bGRuJ3Qg\\nZmluZCBhIGNvbW1lbnRpbmcgcGx1Z2luIHRoYXQgbGV2ZXJhZ2VkIHRoYXQK\\nZmVhdHVyZSAoSSBvdmVybG9va2VkClt0Y29tbWVudC52aW1dKGh0dHBzOi8v\\nZ2l0aHViLmNvbS90b210b20vdGNvbW1lbnRfdmltKSkuICBTdHJpdmluZyBm\\nb3IKbWluaW1hbGlzbSwgaXQgd2VpZ2hzIGluIGF0IHVuZGVyIDEwMCBsaW5l\\ncyBvZiBjb2RlLgoKT2gsIGFuZCBpdCB1bmNvbW1lbnRzLCB0b28uICBUaGUg\\nYWJvdmUgbWFwcyBhY3R1YWxseSB0b2dnbGUsIGFuZCBgZ2NnY2AKdW5jb21t\\nZW50cyBhIHNldCBvZiBhZGphY2VudCBjb21tZW50ZWQgbGluZXMuCgojIyBJ\\nbnN0YWxsYXRpb24KCkluc3RhbGwgdXNpbmcgeW91ciBmYXZvcml0ZSBwYWNr\\nYWdlIG1hbmFnZXIsIG9yIHVzZSBWaW0ncyBidWlsdC1pbiBwYWNrYWdlCnN1\\ncHBvcnQ6CgogICAgbWtkaXIgLXAgfi8udmltL3BhY2svdHBvcGUvc3RhcnQK\\nICAgIGNkIH4vLnZpbS9wYWNrL3Rwb3BlL3N0YXJ0CiAgICBnaXQgY2xvbmUg\\naHR0cHM6Ly90cG9wZS5pby92aW0vY29tbWVudGFyeS5naXQKICAgIHZpbSAt\\ndSBOT05FIC1jICJoZWxwdGFncyBjb21tZW50YXJ5L2RvYyIgLWMgcQoKTWFr\\nZSBzdXJlIHRoaXMgbGluZSBpcyBpbiB5b3VyIHZpbXJjLCBpZiBpdCBpc24n\\ndCBhbHJlYWR5OgoKICAgIGZpbGV0eXBlIHBsdWdpbiBpbmRlbnQgb24KCiMj\\nIEZBUQoKPiBNeSBmYXZvcml0ZSBmaWxlIHR5cGUgaXNuJ3Qgc3VwcG9ydGVk\\nIQoKUmVsYXghICBZb3UganVzdCBoYXZlIHRvIGFkanVzdCBgJ2NvbW1lbnRz\\ndHJpbmcnYDoKCiAgICBhdXRvY21kIEZpbGVUeXBlIGFwYWNoZSBzZXRsb2Nh\\nbCBjb21tZW50c3RyaW5nPSNcICVzCgojIyBTZWxmLVByb21vdGlvbgoKTGlr\\nZSBjb21tZW50YXJ5LnZpbT8gRm9sbG93IHRoZSByZXBvc2l0b3J5IG9uCltH\\naXRIdWJdKGh0dHBzOi8vZ2l0aHViLmNvbS90cG9wZS92aW0tY29tbWVudGFy\\neSkgYW5kIHZvdGUgZm9yIGl0IG9uClt2aW0ub3JnXShodHRwOi8vd3d3LnZp\\nbS5vcmcvc2NyaXB0cy9zY3JpcHQucGhwP3NjcmlwdF9pZD0zNjk1KS4gIEFu\\nZCBpZgp5b3UncmUgZmVlbGluZyBlc3BlY2lhbGx5IGNoYXJpdGFibGUsIGZv\\nbGxvdyBbdHBvcGVdKGh0dHA6Ly90cG8ucGUvKSBvbgpbVHdpdHRlcl0oaHR0\\ncDovL3R3aXR0ZXIuY29tL3Rwb3BlKSBhbmQKW0dpdEh1Yl0oaHR0cHM6Ly9n\\naXRodWIuY29tL3Rwb3BlKS4KCiMjIExpY2Vuc2UKCkNvcHlyaWdodCAoYykg\\nVGltIFBvcGUuICBEaXN0cmlidXRlZCB1bmRlciB0aGUgc2FtZSB0ZXJtcyBh\\ncyBWaW0gaXRzZWxmLgpTZWUgYDpoZWxwIGxpY2Vuc2VgLgo=\\n";

//        String s = "IyBIZXJva3UgYnVpbGRwYWNrOiBSdWJ5OiB0cG9wZSBlZGl0aW9uCgpUaGlz\\nIGlzIG15IGZvcmsgb2YgdGhlIFtvZmZpY2lhbCBIZXJva3UgUnVieQpidWls\\nZHBhY2tdKGh0dHBzOi8vZ2l0aHViLmNvbS9oZXJva3UvaGVyb2t1LWJ1aWxk\\ncGFjay1ydWJ5KS4gIFVzZSBpdCB3aGVuCmNyZWF0aW5nIGEgbmV3IGFwcDoK\\nCiAgICBoZXJva3UgY3JlYXRlIG15YXBwIC0tYnVpbGRwYWNrIFwKICAgICAg\\naHR0cHM6Ly9naXRodWIuY29tL3Rwb3BlL2hlcm9rdS1idWlsZHBhY2stcnVi\\neS10cG9wZQoKT3IgYWRkIGl0IHRvIGFuIGV4aXN0aW5nIGFwcDoKCiAgICBo\\nZXJva3UgY29uZmlnOmFkZCBcCiAgICAgIEJVSUxEUEFDS19VUkw9aHR0cHM6\\nLy9naXRodWIuY29tL3Rwb3BlL2hlcm9rdS1idWlsZHBhY2stcnVieS10cG9w\\nZQoKT3IganVzdCBjaGVycnktcGljayB0aGUgcGFydHMgeW91IGxpa2UgaW50\\nbyB5b3VyIG93biBmb3JrLgoKQ29udGFpbmVkIHdpdGhpbiBhcmUgYSBmZXcg\\ndGlueSBidXQgc2lnbmlmaWNhbnQgZGlmZmVyZW5jZXMgZnJvbSB0aGUgb2Zm\\naWNpYWwKdmVyc2lvbiwgZGlzdGlsbGVkIGZyb20gcHJvamVjdC1zcGVjaWZp\\nYyBidWlsZHBhY2tzIEkndmUgY3JlYXRlZCBpbiB0aGUgcGFzdC4KCiMjIEN1\\nc3RvbSBjb21waWxhdGlvbiB0YXNrcwoKSWYgdGhlIGBDT01QSUxFX1RBU0tT\\nYCBjb25maWcgdmFyaWFibGUgaXMgc2V0LCBpdCB3aWxsIGJlIHBhc3NlZCB2\\nZXJiYXRpbSB0byBhCmByYWtlYCBpbnZvY2F0aW9uLgoKWW91IGNhbiB1c2Ug\\ndGhpcyBmb3IgYWxsIHNvcnRzIG9mIHRoaW5ncy4gIE15IGZhdm9yaXRlIGlz\\nIGBkYjptaWdyYXRlYC4KCiMjIyBEYXRhYmFzZSBtaWdyYXRpb24gZHVyaW5n\\nIGNvbXBpbGF0aW9uCgpMZXQncyB0YWtlIGEgbG9vayBhdCB0aGUgc3RhbmRh\\ncmQgYmVzdCBwcmFjdGljZSBmb3IgZGVwbG95aW5nIFJhaWxzIGFwcHMgdG8K\\nSGVyb2t1OgoKMS4gIGBoZXJva3UgbWFpbnRlbmFuY2U6b25gLgoyLiAgYGdp\\ndCBwdXNoIGhlcm9rdSBtYXN0ZXJgLiAgVGhpcyByZXN0YXJ0cyB0aGUgYXBw\\nbGljYXRpb24gd2hlbiBjb21wbGV0ZS4gIElmCiAgICB5b3UgaGF2ZSBhbnkg\\nc2NoZW1hIGFkZGl0aW9ucywgeW91ciBhcHAgaXMgbm93IGJyb2tlbiAoaGVu\\nY2UgdGhlIG5lZWQgZm9yCiAgICBtYWludGVuYW5jZSBtb2RlKS4KMy4gIGBo\\nZXJva3UgcnVuIHJha2UgZGI6bWlncmF0ZWAuCjQuICBgaGVyb2t1IHJlc3Rh\\ncnRgLiAgVGhpcyBpcyBuZWNlc3Nhcnkgc28gdGhlIGFwcCBwaWNrcyB1cCBv\\nbiB0aGUgc2NoZW1hCiAgICBjaGFuZ2VzLgo1LiAgYGhlcm9rdSBtYWludGVu\\nYW5jZTpvZmZgLgoKVGhhdCdzIGZpdmUgZGlmZmVyZW50IGNvbW1hbmRzLCBu\\nb25lIG9mIHRoZW0gaW5zdGFudGFuZW91cywgYW5kIHR3byByZXN0YXJ0cy4K\\nVGhlIG1vc3QgY29tbW9uIHJlc3BvbnNlIHRvIHRoaXMgbWVzcyBpcyB0byB3\\ncmFwIGRlcGxveW1lbnQgdXAgaW4gYSBSYWtlIHRhc2ssCmJ1dCBub3cgeW91\\nIGhhdmUgdHdvIHByb2JsZW1zOiBhIHN1Ym9wdGltYWwgZGVwbG95bWVudCBw\\ncm9jZWR1cmUsIGFuZAphcHBsaWNhdGlvbiBjb2RlIGNvbmNlcm5lZCB3aXRo\\nIGRlcGxveW1lbnQuCgpOb3cgbGV0J3MgdGFrZSBhIGxvb2sgYXQgYSB0eXBp\\nY2FsIGRlcGxveSB3aGVuIGBDT01QSUxFX1RBU0tTYCBpbmNsdWRlcwpgZGI6\\nbWlncmF0ZWA6CgoxLiAgYGdpdCBwdXNoIGhlcm9rdSBtYXN0ZXJgLgogICAg\\nKiBGaXJzdCB0aGUgc3RhbmRhcmQgc3R1ZmYgaGFwcGVucy4gIEJ1bmRsaW5n\\nLCBhc3NldCBwcmVjb21waWxhdGlvbiwgdGhhdAogICAgICBzb3J0IG9mIHRo\\naW5nLgogICAgKiBgcmFrZSBkYjptaWdyYXRlYCBmaXJlcy4gIFRoZSBhcHAg\\nY29udGludWVzIHdvcmtpbmcgdW5sZXNzIHRoZQogICAgICBtaWdyYXRpb25z\\nIGRyb3Agc29tZXRoaW5nIGZyb20gdGhlIHNjaGVtYS4KICAgICogVGhlIGFw\\ncCByZXN0YXJ0cy4gIEV2ZXJ5dGhpbmcgaXMgd29uZGVyZnVsLgoKV2UndmUg\\ncmVkdWNlZCBpdCB0byBhIHNpbmdsZSBzdGVwLCBsaW1pdGluZyBvdXIgbmVl\\nZCBmb3IgbWFpbnRlbmFuY2UgbW9kZSB0bwpkZXN0cnVjdGl2ZSBtaWdyYXRp\\nb25zLiAgRXZlbiBpbiB0aGF0IGNhc2UsIGl0J3Mgbm90IGFsd2F5cyBzdHJp\\nY3RseQpuZWNlc3NhcnksIHNpbmNlIHRoZSB3aW5kb3cgZm9yIGJyZWFrYWdl\\nIGlzIGZyZXF1ZW50bHkgb25seSBhIGZldyBzZWNvbmRzLiAgT3IKd2l0aCBh\\nIGJpdCBvZiBwbGFubmluZywgeW91IGNhbiBbYXZvaWQgdGhpcyBzaXR1YXRp\\nb24gZW50aXJlbHldW25vIGRvd250aW1lXS4KCltUd2VsdmUtZmFjdG9yXVtd\\nIHNub2JzIChvZiB3aGljaCBJIGFtIG9uZSkgd291bGQgZ2VuZXJhbGx5IGFy\\nZ3VlIHRoYXQKW2FkbWluIHByb2Nlc3Nlc11bXSBiZWxvbmcgaW4gdGhlIHJ1\\nbiBzdGFnZSwgbm90IHRoZSBidWlsZCBzdGFnZS4gIEkgYWdyZWUgaW4KdGhl\\nb3J5LCBidXQgaXQgaW4gcHJhY3RpY2UsIGJveSBkb2VzIHRoaXMgbWFrZSB0\\naGluZ3MgYSB3aG9sZSBsb3Qgc2ltcGxlci4KCltubyBkb3dudGltZV06IGh0\\ndHA6Ly9wZWRyby5oZXJva3VhcHAuY29tL3Bhc3QvMjAxMS83LzEzL3JhaWxz\\nX21pZ3JhdGlvbnNfd2l0aF9ub19kb3dudGltZS8KW1R3ZWx2ZS1mYWN0b3Jd\\nOiBodHRwOi8vd3d3LjEyZmFjdG9yLm5ldC8KW0FkbWluIHByb2Nlc3Nlc106\\nIGh0dHA6Ly93d3cuMTJmYWN0b3IubmV0L2FkbWluLXByb2Nlc3NlcwoKIyMg\\nQ29tbWl0IHJlY29yZGluZwoKKipCcm9rZW4gYW5kIGRpc2FibGVkIHBlbmRp\\nbmcgZnVydGhlciBpbnZlc3RpZ2F0aW9uLioqCgpUaGlzIHRha2VzIHRoZSB1\\ncGNvbWluZyBhbmQgcHJldmlvdXNseSBkZXBsb3llZCBjb21taXQgU0hBcyBh\\nbmQgbWFrZXMgdGhlbQphdmFpbGFibGUgYXMgYCRSRVZJU0lPTmAgYW5kIGAk\\nT1JJR0lOQUxfUkVWSVNJT05gIGZvciB0aGUgZHVyYXRpb24gb2YgdGhlCmNv\\nbXBpbGUuICBUaGV5IGFyZSBhbHNvIHdyaXR0ZW4gdG8gYEhFQURgIGFuZCBg\\nT1JJR19IRUFEYCBpbiB0aGUgcm9vdCBvZiB0aGUKYXBwbGljYXRpb24gZm9y\\nIGVhc3kgYWNjZXNzIGFmdGVyIHRoZSBkZXBsb3kgaXMgY29tcGxldGUuCgpU\\naGVzZSBjYW4gYmUgdXNlZCBmcm9tIGBDT01QSUxFX1RBU0tTYCB0byBtYWtl\\nIGEgcG9vciBtYW4ncyBwb3N0LWRlcGxveSBob29rLgo=\\n";

//        System.out.println(base64(s));
//        String autotest1 = base64(s1);
//        String autotest2 = base64(s2);
//
////        System.out.println(autotest);
//
//        KimiDto kimiDto = new KimiDto();
//        kimiDto.setModel("moonshot-v1-32k");
//
//        List<KimiMessage> list = new ArrayList<>();
//
//        KimiMessage kimiMessage = new KimiMessage();
//        kimiMessage.setRole("user");
//        kimiMessage.setContent(autotest1);
//
//        KimiMessage kimiMessage1 = new KimiMessage();
//        kimiMessage1.setRole("user");
//        kimiMessage1.setContent(autotest2);
//
//        KimiMessage kimiMessage2 = new KimiMessage();
//        kimiMessage2.setRole("user");
//        kimiMessage2.setContent("请根据以上内容，简要介绍作者的技术特点");
//
//        list.add(kimiMessage);
//        list.add(kimiMessage1);
//        list.add(kimiMessage2);
//
//
//        kimiDto.setMessages(list);
//
//        String jsonString = JSON.toJSONString(kimiDto);
//        System.out.println(jsonString);
//        String s = request_kimi(jsonString);
//        System.out.println(s);

//        getKimiEvaluate("tpope");
//
//    }
}