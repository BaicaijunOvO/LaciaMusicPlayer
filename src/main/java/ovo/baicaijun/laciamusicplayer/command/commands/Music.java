package ovo.baicaijun.laciamusicplayer.command.commands;

import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;
import ovo.baicaijun.laciamusicplayer.command.Command;
import ovo.baicaijun.laciamusicplayer.music.MusicManager;
import ovo.baicaijun.laciamusicplayer.music.MusicPlayer;
import ovo.baicaijun.laciamusicplayer.music.netease.NeteaseLoginManager;
import ovo.baicaijun.laciamusicplayer.music.netease.NeteaseMusicLoader;
import ovo.baicaijun.laciamusicplayer.util.MessageUtil;

import java.io.File;

/**
 * @author BaicaijunOvO
 * @date 2025/08/25 16:46
 * @modified 修复了 pause, resume, volume, 和 reload 命令的逻辑。
 **/
public class Music extends Command {
    public Music() {
        super(new String[] {"music"});
    }
    public static MusicPlayer player = LaciamusicplayerClient.musicPlayer;


    @Override
    public void run(String[] args) {
        if (args.length == 0) {
            MessageUtil.sendMessage("§c用法: /music <load|list|play|stop|pause|resume|volume|reload>");

            return;
        }

        String subCommand = args[0].toLowerCase(); // 统一转为小写，增加健壮性

        if (subCommand.equals("load")) {
            if (args.length < 2) {
                MessageUtil.sendMessage("§c用法: /music load <歌曲名>");
                return;
            }
            if (!MusicManager.musics.containsKey(args[1])){
                MessageUtil.sendMessage("§c未找到歌曲: " + args[1]);
                return;
            }
            File file = MusicManager.musics.get(args[1]).getFile();
            if (!file.exists()) {
                MessageUtil.sendMessage("§c错误：歌曲文件不存在！");
                return;
            }
            player.load(file,null,null);
            player.play();

        } else if (subCommand.equals("list")){
            MessageUtil.sendMessage("§a--- 歌曲列表 ---");
            MusicManager.musics.forEach(((name, data) -> MessageUtil.sendMessage("§7- " + name)));

        } else if (subCommand.equals("play")) {
            // 注意：play现在只用于从头开始播放已加载的歌曲
            player.play();

        } else if (subCommand.equals("stop")) {
            player.stop();

        } else if (subCommand.equals("pause")) {
            player.pause();

        } else if (subCommand.equals("resume")) {
            // 修复：resume 和 pause 应该调用同一个方法，因为它是一个切换器
            player.pause();

        } else if (subCommand.equals("volume")) {
            if (args.length < 2) {
                MessageUtil.sendMessage("§c用法: /music volume <0-130>");
                return;
            }
            try {
                int volume = Integer.parseInt(args[1]);
                player.setVolume(volume);
            } catch (NumberFormatException e) {
                MessageUtil.sendMessage("§c请输入一个有效的数字作为音量！");
            }

        } else if (subCommand.equals("reload")) {
            MusicManager.reload();
            LaciamusicplayerClient.configManager.reload();
            NeteaseMusicLoader.reload();

        }else if (subCommand.equals("qrcode")) {
            NeteaseLoginManager.startQRLogin();

        }else if(subCommand.equals("cookie")){
            NeteaseMusicLoader.setCookie(args[1]);
            MessageUtil.sendMessage("将cookie设置为: " + args[1]);
        } else {
            MessageUtil.sendMessage("§c未知命令: " + args[0]);
        }
    }
}