-- ============================================================================
-- PetCare O2O 真实演示媒体与内容种子（data-media.sql）
--
-- 用法：在 schema.sql + data-dev.sql 之后执行；可重复执行（幂等）。
--   本地：docker exec -i petcare-mysql mysql -uroot -p<pwd> petcare_o2o < data-media.sql
--   或   mysql -u root -p petcare_o2o < src/main/resources/data-media.sql
--
-- 内容：
--   1. 服务/商品封面与相册（替换 /static/logo.png 占位图）
--   2. 商品文案重写 + 新增湿粮罐头分类与商品（5011/5012）
--   3. 社区帖子 10 条（含图片）、评论 42 条、点赞/收藏
--   4. 新增演示用户 4 名（密码同为 user123456）与宠物档案
--
-- 图片：存放于 {后端工作目录}/uploads/images/seed/**（仓库已提交），
--   由后端 /uploads/** 静态资源映射对外提供，H5 经 Vite 代理访问。
--   许可：全部来自 Wikimedia Commons（CC0/CC BY/CC BY-SA/PD）与
--   Unsplash（Unsplash License），出处见 uploads/images/seed/CREDITS.md。
-- ============================================================================

USE petcare_o2o;

-- ============================================================================
-- 1. 服务项目：封面 + 丰富描述（名称/价格/状态不变）
-- ============================================================================
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-dog-bath.jpg',    description = '小型犬（≤10kg）基础洗护：温水浴 + 宠物专用低敏香波 + 低温吹干 + 基础梳理 + 耳道清洁。低应激手法，全程约 60 分钟，洗完毛发蓬松无打结。' WHERE id = 3001;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-salon.jpg',       description = '中型犬（10-25kg）深层洗护：深层清洁 + 护毛素 + 大功率吹水机吹干 + 毛发梳理 + 耳道清洁 + 剪指甲。适合运动量大、易出油的狗狗。' WHERE id = 3002;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-cat-brush.jpg',   description = '猫专用低应激洗护：温水浴或免洗泡沫可选，低噪音吹风机吹干，含全身梳毛与浮毛清理。全程一名洗护师一对一，不笼养等待。' WHERE id = 3003;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-poodle-groom.jpg',description = '美容师 1v1 造型修剪（贵宾/比熊/博美等），含洗澡吹干。可指定造型风格（泰迪装/蘑菇头等），修剪后附赠拍照留念。' WHERE id = 3004;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-cat-pet.jpg',     description = '猫咪局部美容：腹底毛、脚底毛、肛周毛精细修剪，不剃背毛，减少应激。使用静音直剪，适合胆小猫咪的第一次美容。' WHERE id = 3005;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-dog-walk.jpg',    description = '持证遛狗师上门，标准牵引装备，30 分钟户外遛弯。遛后喂水、擦脚，附如厕与精神状态记录反馈给主人。限门店 5 公里内。' WHERE id = 3006;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-cat-feed.jpg',    description = '上门喂猫：换水、喂粮、铲屎，可加拍短视频反馈。钥匙支持加密保管，适合短途出差与节假日出行。' WHERE id = 3007;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-dog-walk2.jpg',   description = '上门综合照护：喂养 + 陪伴玩耍 + 基础清洁三合一，适合多宠家庭与长期出差。服务报告当日反馈。' WHERE id = 3008;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-daycare.jpg',     description = '日间寄养：独立笼位、两餐喂食与上下午户外活动时间，店内监控全程可查看。需提前一天预约并携带疫苗本。' WHERE id = 3009;
UPDATE `service_item` SET cover_url = '/uploads/images/seed/svc/svc-golden.jpg',      description = '大型犬（25kg+）专属洗护：加长洗护台 + 大功率吹水机，两名洗护师协作完成，含深层清洁与全身梳理。' WHERE id = 3010;

-- 服务相册（详情页“服务详情”图）
DELETE FROM `service_item_image` WHERE id BETWEEN 31001 AND 31020;
INSERT INTO `service_item_image` (`id`, `service_item_id`, `image_url`, `sort`) VALUES
(31001, 3001, '/uploads/images/seed/svc/svc-dog-bath.jpg',      1),
(31002, 3001, '/uploads/images/seed/svc/svc-salon.jpg',         2),
(31003, 3002, '/uploads/images/seed/svc/svc-salon.jpg',         1),
(31004, 3002, '/uploads/images/seed/svc/svc-dog-groomer.jpg',   2),
(31005, 3003, '/uploads/images/seed/svc/svc-cat-brush.jpg',     1),
(31006, 3003, '/uploads/images/seed/svc/svc-cat-pet.jpg',       2),
(31007, 3004, '/uploads/images/seed/svc/svc-poodle-groom.jpg',  1),
(31008, 3004, '/uploads/images/seed/svc/svc-yorkie-haircut.jpg',2),
(31009, 3005, '/uploads/images/seed/svc/svc-cat-pet.jpg',       1),
(31010, 3005, '/uploads/images/seed/svc/svc-cat-brush.jpg',     2),
(31011, 3006, '/uploads/images/seed/svc/svc-dog-walk.jpg',      1),
(31012, 3006, '/uploads/images/seed/svc/svc-dog-walk2.jpg',     2),
(31013, 3007, '/uploads/images/seed/svc/svc-cat-feed.jpg',      1),
(31014, 3007, '/uploads/images/seed/post/post-cat-eat.jpg',     2),
(31015, 3008, '/uploads/images/seed/svc/svc-dog-walk2.jpg',     1),
(31016, 3008, '/uploads/images/seed/svc/svc-cat-feed.jpg',      2),
(31017, 3009, '/uploads/images/seed/svc/svc-daycare.jpg',       1),
(31018, 3009, '/uploads/images/seed/svc/svc-daycare2.jpg',      2),
(31019, 3010, '/uploads/images/seed/svc/svc-golden.jpg',        1),
(31020, 3010, '/uploads/images/seed/svc/svc-dog-bath.jpg',      2);

-- ============================================================================
-- 2. 商品：封面 + 电商级文案（价格/库存/状态不变）
-- ============================================================================
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-dog-kibble.jpg', description = '法国皇家中型成犬专用配方（10 月龄以上），专属梅花颗粒促进咀嚼、减缓进食；添加 Omega-3 与 EPA/DHA 帮助维持皮肤屏障，均衡膳食纤维支持肠道健康。适用于边牧、柴犬、柯基等中型犬日常主食。' WHERE id = 5001;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-cat-kibble.jpg', description = '90% 鲜肉含量、无谷无豆配方，前五位原料均为新鲜鸡肉；富含牛磺酸与 Omega-3，支持猫咪泌尿系统与毛发健康。高蛋白低碳水，适合全年龄段猫咪，挑嘴猫适口性实测好评。' WHERE id = 5002;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-dogfood2.jpg', description = '多种鲜肉来源（散养鸡肉、火鸡、鲜鱼），60% 肉类含量；添加南瓜与蔓越莓支持消化与泌尿健康，适合对单一肉源敏感的狗狗。' WHERE id = 5003;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-dog-treats.jpg', description = '单一鸡胸肉原切冻干，-36℃ 真空冻干锁鲜；无诱食剂、无防腐剂、无添加糖。掰碎可做训练奖励，整条可作磨牙啃食，猫犬通用。' WHERE id = 5004;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-cat-treat.jpg', description = '金枪鱼 / 鸡肉 / 三文鱼三种口味混装，添加牛磺酸与维生素 E；撕口设计单手可喂，是剪指甲、刷牙、喂药时的救命道具。建议每日不超过 3 支。' WHERE id = 5005;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-bone-treats.jpg', description = '低温烘焙鸭肉绕棒，硬度适中，咀嚼过程帮助清洁牙垢、清新口气；无淀粉添加，小型犬与中大型犬幼犬均适用。' WHERE id = 5006;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-dog-bowl.jpg', description = '304 食品级不锈钢一体成型双碗，水粮分离；底座加防滑硅胶圈，进食不推移，碗沿卷边设计不伤胡须，可整体拆洗。' WHERE id = 5007;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-litter.jpg', description = '半封闭大号猫砂盆，含沥水踏板减少带砂；微负压导流设计防止气味扩散，适合 5 公斤以上大体型猫咪，可配团凝豆腐砂或膨润土。' WHERE id = 5008;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-nail.jpg', description = '静音款宠物指甲钳，锋利不锈钢刀头一次成型，剪切面贴合指甲弧度不劈裂；带限位挡板防剪到血线，附锉面打磨毛边。' WHERE id = 5009;
UPDATE `product` SET cover_url = '/uploads/images/seed/prod/prod-leash.jpg', description = '高强度尼龙编织牵引绳，1.2 米可调节，配减压胸背扣位；手柄人体工学加厚泡棉，大型犬爆冲也不勒手。多色可选。' WHERE id = 5010;

-- 新分类：湿粮罐头 + 两款主食罐（用户要求的“罐头”类目）
DELETE FROM `product_category` WHERE id = 4004;
INSERT INTO `product_category` (`id`, `name`, `sort`, `status`) VALUES
(4004, '湿粮罐头', 2, 'ACTIVE');

DELETE FROM `product` WHERE id IN (5011, 5012);
INSERT INTO `product` (`id`, `category_id`, `name`, `price`, `stock`, `sales_count`, `description`, `cover_url`, `pickup_only`, `status`, `sort`) VALUES
(5011, 4004, '主食猫罐头 吞拿鱼+鸡肉 85g×6', 45.00, 60, 88,
 '主食级配方（非零食罐），满足 AAFCO 成猫全价营养标准；95% 含肉量 + 牛磺酸 + 维生素 E，汤汁充足，专治不爱喝水的猫；无谷物、无诱食剂、无胶质添加。吞拿鱼与鸡肉双口味混装。',
 '/uploads/images/seed/prod/prod-can-cat.jpg', 1, 'ON_SALE', 1),
(5012, 4004, '幼猫鲜肉慕斯餐盒 95g×4', 32.00, 45, 52,
 '细腻慕斯质地，离乳期至 12 月龄幼猫专用；单一鸡肉蛋白低敏好消化，添加 DHA 支持大脑与视力发育，小份独立包装一次一盒不浪费。',
 '/uploads/images/seed/prod/prod-kattmat.jpg', 1, 'ON_SALE', 2);

-- 商品相册（详情页轮播）
DELETE FROM `product_image` WHERE id BETWEEN 51001 AND 51040;
INSERT INTO `product_image` (`id`, `product_id`, `image_url`, `sort`) VALUES
(51001, 5001, '/uploads/images/seed/prod/prod-dog-kibble.jpg',   1),
(51002, 5001, '/uploads/images/seed/prod/prod-dogfood2.jpg',     2),
(51003, 5001, '/uploads/images/seed/prod/prod-dog-bowl.jpg',     3),
(51004, 5002, '/uploads/images/seed/prod/prod-cat-kibble.jpg',   1),
(51005, 5002, '/uploads/images/seed/prod/prod-cat-kibble2.jpg',  2),
(51006, 5002, '/uploads/images/seed/prod/prod-cat-kibble3.jpg',  3),
(51007, 5003, '/uploads/images/seed/prod/prod-dogfood2.jpg',     1),
(51008, 5003, '/uploads/images/seed/prod/prod-dog-kibble.jpg',   2),
(51009, 5004, '/uploads/images/seed/prod/prod-dog-treats.jpg',   1),
(51010, 5004, '/uploads/images/seed/prod/prod-dog-treat-bowl.jpg',2),
(51011, 5005, '/uploads/images/seed/prod/prod-cat-treat.jpg',    1),
(51012, 5005, '/uploads/images/seed/post/post-cat-eat.jpg',      2),
(51013, 5006, '/uploads/images/seed/prod/prod-bone-treats.jpg',  1),
(51014, 5006, '/uploads/images/seed/prod/prod-dog-treat-bowl.jpg',2),
(51015, 5007, '/uploads/images/seed/prod/prod-dog-bowl.jpg',     1),
(51016, 5007, '/uploads/images/seed/prod/prod-slow-bowl.jpg',    2),
(51017, 5008, '/uploads/images/seed/prod/prod-litter.jpg',       1),
(51018, 5008, '/uploads/images/seed/prod/prod-litterbox.jpg',    2),
(51019, 5009, '/uploads/images/seed/prod/prod-nail.jpg',         1),
(51020, 5010, '/uploads/images/seed/prod/prod-leash.jpg',        1),
(51021, 5010, '/uploads/images/seed/svc/svc-dog-walk.jpg',       2),
(51022, 5011, '/uploads/images/seed/prod/prod-can-cat.jpg',      1),
(51023, 5011, '/uploads/images/seed/prod/prod-can-cat2.jpg',     2),
(51024, 5011, '/uploads/images/seed/prod/prod-royal-canin.jpg',  3),
(51025, 5012, '/uploads/images/seed/prod/prod-kattmat.jpg',      1),
(51026, 5012, '/uploads/images/seed/prod/prod-can-cat2.jpg',     2);

-- 商品介绍图（详情页“商品介绍”区块）
DELETE FROM `product_detail_image` WHERE id BETWEEN 51501 AND 51540;
INSERT INTO `product_detail_image` (`id`, `product_id`, `image_url`, `sort`) VALUES
(51501, 5001, '/uploads/images/seed/prod/prod-dogfood2.jpg',     1),
(51502, 5001, '/uploads/images/seed/prod/prod-dog-begging.jpg',  2),
(51503, 5002, '/uploads/images/seed/prod/prod-cat-kibble2.jpg',  1),
(51504, 5002, '/uploads/images/seed/prod/prod-cat-kibble3.jpg',  2),
(51505, 5003, '/uploads/images/seed/prod/prod-dog-bowl.jpg',     1),
(51506, 5004, '/uploads/images/seed/prod/prod-dog-treat-bowl.jpg',1),
(51507, 5004, '/uploads/images/seed/prod/prod-dog-begging.jpg',  2),
(51508, 5005, '/uploads/images/seed/post/post-cat-eat.jpg',      1),
(51509, 5006, '/uploads/images/seed/prod/prod-dog-treats.jpg',   1),
(51510, 5007, '/uploads/images/seed/prod/prod-slow-bowl.jpg',    1),
(51511, 5008, '/uploads/images/seed/prod/prod-litterbox.jpg',    1),
(51512, 5009, '/uploads/images/seed/prod/prod-nail.jpg',         1),
(51513, 5010, '/uploads/images/seed/svc/svc-dog-walk.jpg',       1),
(51514, 5011, '/uploads/images/seed/prod/prod-royal-canin.jpg',  1),
(51515, 5011, '/uploads/images/seed/prod/prod-can-cat2.jpg',     2),
(51516, 5012, '/uploads/images/seed/prod/prod-can-cat2.jpg',     1);

-- 商品展示页轮播横幅
DELETE FROM `product_carousel_image` WHERE id BETWEEN 52001 AND 52003;
INSERT INTO `product_carousel_image` (`id`, `title`, `image_url`, `link_type`, `link_target_id`, `status`, `sort`) VALUES
(52001, '进口主粮节 · 第二件半价', '/uploads/images/seed/banner/banner-golden-wide.jpg', 'PRODUCT', 5001, 'ACTIVE', 1),
(52002, '主食罐新品首发 · 骗水神器', '/uploads/images/seed/banner/banner-dogs.jpg',      'PRODUCT', 5011, 'ACTIVE', 2),
(52003, '冻干零食上新 · 训练必备', '/uploads/images/seed/banner/banner-husky-wide.jpg', 'PRODUCT', 5004, 'ACTIVE', 3);

-- 营销活动封面（data-dev.sql 已引用 /uploads/images/activity-*.jpg，此处仅确保文件存在，无需改库）

-- ============================================================================
-- 3. 演示用户（密码同 user123456）与宠物档案
-- ============================================================================
UPDATE `user` SET avatar_url = '/uploads/images/seed/avatar/avatar-dog-golden.jpg' WHERE id = 10001;
UPDATE `user` SET avatar_url = '/uploads/images/seed/avatar/avatar-cat-tabby.jpg' WHERE id = 10002;

DELETE FROM `pet` WHERE id BETWEEN 11003 AND 11006;
DELETE FROM `user` WHERE id BETWEEN 10003 AND 10006;
INSERT INTO `user` (`id`, `openid`, `nickname`, `avatar_url`, `phone`, `password_hash`, `gender`, `status`) VALUES
(10003, NULL, '柯基娘·七崽',     '/uploads/images/seed/post/post-corgi.jpg',              '13800138003', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 2, 'ACTIVE'),
(10004, NULL, '阿杰不打游戏',   '/uploads/images/seed/avatar/avatar-dog-brown.jpg',      '13800138004', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 1, 'ACTIVE'),
(10005, NULL, '两只猫的打工人', '/uploads/images/seed/post/post-kitten.jpg',             '13800138005', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 2, 'ACTIVE'),
(10006, NULL, '雪橇三傻之一的爹','/uploads/images/seed/post/post-husky.jpg',             '13800138006', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 1, 'ACTIVE');

INSERT INTO `pet` (`id`, `user_id`, `name`, `type`, `breed`, `gender`, `age`, `weight`, `size`, `sterilized`, `remark`) VALUES
(11003, 10003, '七崽', 'DOG', '威尔士柯基',  2, 2.0, 12.40, 'MEDIUM', 1, '爆毛期，喜欢追扫地机器人'),
(11004, 10004, '糯米', 'DOG', '迷你贵宾',    1, 2.0,  5.80, 'SMALL',  0, '爱照镜子，吹完造型更明显'),
(11005, 10005, '芝麻', 'CAT', '狸花猫',      1, 0.3,  1.10, 'SMALL',  0, '车库捡的，还有个兄弟叫汤圆'),
(11006, 10006, '拆拆', 'DOG', '哈士奇',      1, 1.5, 22.00, 'LARGE',  0, '拆家战绩：床垫/沙发扶手/数据线×4');

-- ============================================================================
-- 4. 社区：新话题 + 帖子（真实口吻、图文、互动数据）
-- ============================================================================
DELETE FROM `topic` WHERE id = 6004;
INSERT INTO `topic` (`id`, `name`, `description`, `sort`, `status`) VALUES
(6004, '好物分享', '粮食罐头零食开箱与真实测评', 4, 'ACTIVE');

-- 4.1 原有 3 帖内容加深 + 配图
UPDATE `post` SET
  title = '带三岁的金毛去洗了个澡，出来直接蓬成一个球',
  content = '豆豆平时最怕吹风机，这次张师傅全程用手护着它耳朵慢慢吹，居然一次都没挣扎。洗完毛发蓬松得像刚出炉的面包，回家路上一直疯狂摇尾巴，见谁都打招呼。给大家看看洗护台上的样子，顺便问一句这个蓬松度大概能撑几天哈哈。店里还送了梳毛服务，指缝里的浮毛都梳出来了，对金毛家长来说简直是掉毛期救星。',
  view_count = 342, like_count = 57, comment_count = 4, favorite_count = 9,
  publish_time = NOW() - INTERVAL 12 DAY
WHERE id = 7001;

UPDATE `post` SET
  title = '英短到底要不要做美容？亲测一年后说句公道话',
  content = '去年这时候我还觉得猫咪美容是智商税，直到咪咪换毛季掉得满屋飞毛。第一次做的是局部清理（腹底毛+脚底毛+肛周），全程大概四十分钟，师傅用直剪没有上推子，咪咪只是耳朵一直转来转去但没有哈气。做完最明显的变化：吐毛球从一周两次降到两周一次，家里浮毛肉眼可见变少。个人建议：长毛猫一年 2-3 次，英短这种短毛的换毛季前去一次就够，没必要频繁水洗。附上做完那天的咪咪，毛色亮了一个度。',
  view_count = 518, like_count = 89, comment_count = 5, favorite_count = 26,
  publish_time = NOW() - INTERVAL 9 DAY
WHERE id = 7002;

UPDATE `post` SET
  title = '新手养猫驱虫时间表（兽医朋友给的版本）',
  content = '经常看到有人问驱虫怎么安排，把兽医朋友给我划的重点整理一下：\n1. 体内驱虫：幼猫 6/8/12 周各一次，之后每 3 个月一次；成年猫如果纯室内养、不吃生骨肉，可以放宽到 3-6 个月一次。\n2. 体外驱虫：每月一次滴剂，夏天和南方潮湿地区千万不要停。\n3. 驱虫和疫苗要间隔至少一周，别同一天搞，猫咪肠胃受不了。\n4. 内外同驱的滴剂方便但一定看体重分段，幼猫买错剂量的真的太多了。\n以上是通用方案，具体用药和剂量遵医嘱，体检的时候顺便问一句最稳。配图是接咪咪回家第一周拍的，祝大家的猫都健健康康。',
  view_count = 896, like_count = 134, comment_count = 6, favorite_count = 118,
  publish_time = NOW() - INTERVAL 15 DAY
WHERE id = 7003;

-- 4.2 新增 7 帖
DELETE FROM `post` WHERE id BETWEEN 7004 AND 7010;
INSERT INTO `post` (`id`, `user_id`, `pet_id`, `topic_id`, `title`, `content`, `status`, `risk_level`, `view_count`, `like_count`, `comment_count`, `favorite_count`, `publish_time`) VALUES
(7004, 10003, 11003, 6001, '柯基掉毛季生存指南：我家现在每天都是毛毯',
 '谁能想到一只 11 公斤的狗能掉出 50 公斤的毛。七崽最近进入爆毛期，扫地机器人一天清理三次次次都是满的。自救方案亲测排名：1. 店里的深层洗护（洗完能管两周）2. 自己用针梳每天十分钟 3. 粘毛滚（治标不治本但离不开）。附图是洗完当天，毛发蓬得腿都看不见了，笑死。评论区求更多柯基家长支招。',
 'PUBLISHED', 0, 465, 72, 4, 15, NOW() - INTERVAL 7 DAY),

(7005, 10006, 11006, 6001, '哈士奇又拆家了，这次是宜家床垫',
 '出门俩小时，回家床垫开线，棉花飞得像下雪。拆拆（一岁半，22kg）见到我第一反应是疯狂摇尾巴装无辜，仿佛现场不是它干的。已经不想生气了，发出来让大家开心一下。止吠器、嗅闻垫、冻干塞漏食球都试了，效果最好的还是把它送去日间寄养放电，累一天回来倒头就睡，根本没力气拆。有没有同款哈士奇家长，评论区抱团取暖。',
 'PUBLISHED', 0, 723, 156, 5, 31, NOW() - INTERVAL 5 DAY),

(7006, 10005, 11005, 6003, '楼下捡的两只小奶猫，现在是我全部的业余时间',
 '三周前在小区车库听到叫声，纸箱里两只一个多月大的小狸花。先用羊奶粉过渡，现在已经完全会自己吃泡软的幼猫粮了，取名芝麻和汤圆。带去体检做了驱虫，医生说状态不错就是有点瘦。第一次养这么小的猫，每天睁眼第一件事是去看它们，手机相册已经两千多张。图一是刚捡到的时候，图二是最近，变化真的很大。',
 'PUBLISHED', 0, 651, 118, 6, 44, NOW() - INTERVAL 3 DAY),

(7007, 10004, 11004, 6002, '贵宾洗澡前后判若两狗，图二不是同一只我不信',
 '糯米（迷你贵宾，两岁）进店前是只流浪艺术家，洗完吹完修剪出来直接是顶流爱豆。张师傅把脸上和脚上的毛修完，它连走路都变得自信了，回家对着镜子看了十分钟不肯走。全程一个半小时，去的时候紧张得发抖，出来的时候蹦蹦跳跳。造型费真的值，就是钱包瘦了一圈。',
 'PUBLISHED', 0, 289, 61, 3, 8, NOW() - INTERVAL 2 DAY),

(7008, 10002, 11002, 6004, '囤的 6 款主食罐开箱，咪咪亲自试吃打分',
 '不爱喝水的猫只能靠罐头骗水。这次入了 6 款主食罐，吞拿鱼和鸡肉口味的适口性最好，咪咪全程头都不抬；慕斯质地的适合舔舐慢的老猫，肉块款要先拌碎。挑罐头的小技巧：看配料表前两位是不是明确的整肉，写“肉类及其副产品”这种模糊描述的直接放回货架。附上试吃现场，胡子都吃到打结。',
 'PUBLISHED', 0, 584, 97, 4, 52, NOW() - INTERVAL 6 DAY),

(7009, 10001, 11001, 6001, '周中遛狗偶遇柯基军团，豆豆玩疯了',
 '平时都是晚上遛，今天请了半天假下午去公园，结果赶上柯基聚会。豆豆一个 28 公斤的大狗被四只柯基围追堵截，全程被压着打，尾巴还摇得特别开心。回家路上秒睡，晚饭都没吃完就打呼了。工作日下午的公园人少狗少，简直是狗的淡季乐园，有条件的朋友推荐错峰遛狗。',
 'PUBLISHED', 0, 376, 48, 3, 6, NOW() - INTERVAL 1 DAY),

(7010, 10005, NULL, 6001, '猫咪睡姿大赛报名：liquid cat 现场直击',
 '芝麻最近解锁了新睡姿：摊成一滩。第一张是标准仰面大字型，第二张直接流进纸箱里，第三张是教科书级液体猫姿势。每次想拍照它就换姿势，相册里全是糊图，能抓到这三张已经烧高香了。你们家的猫都是什么睡姿，评论区交出来比一比。',
 'PUBLISHED', 0, 429, 83, 4, 12, NOW());

-- 4.3 帖子配图
DELETE FROM `post_image` WHERE id BETWEEN 71001 AND 71030;
INSERT INTO `post_image` (`id`, `post_id`, `image_url`, `sort`) VALUES
(71001, 7001, '/uploads/images/seed/svc/svc-dog-bath.jpg',      1),
(71002, 7001, '/uploads/images/seed/svc/svc-golden.jpg',        2),
(71003, 7002, '/uploads/images/seed/post/post-british.jpg',     1),
(71004, 7002, '/uploads/images/seed/post/post-british2.jpg',    2),
(71005, 7003, '/uploads/images/seed/post/post-kitten2.jpg',     1),
(71006, 7004, '/uploads/images/seed/post/post-corgi.jpg',       1),
(71007, 7004, '/uploads/images/seed/post/post-corgi2.jpg',      2),
(71008, 7005, '/uploads/images/seed/post/post-husky.jpg',       1),
(71009, 7005, '/uploads/images/seed/post/post-husky2.jpg',      2),
(71010, 7006, '/uploads/images/seed/post/post-kittens.jpg',     1),
(71011, 7006, '/uploads/images/seed/post/post-kitten.jpg',      2),
(71012, 7007, '/uploads/images/seed/svc/svc-poodle-groom.jpg',  1),
(71013, 7007, '/uploads/images/seed/post/post-salon.jpg',       2),
(71014, 7008, '/uploads/images/seed/prod/prod-can-cat.jpg',     1),
(71015, 7008, '/uploads/images/seed/prod/prod-can-cat2.jpg',    2),
(71016, 7008, '/uploads/images/seed/post/post-cat-eat.jpg',     3),
(71017, 7009, '/uploads/images/seed/post/post-dogs-play1.jpg',  1),
(71018, 7009, '/uploads/images/seed/post/post-dogs-play2.jpg',  2),
(71019, 7009, '/uploads/images/seed/post/post-golden-stick.jpg',3),
(71020, 7010, '/uploads/images/seed/post/post-cat-sleep.jpg',   1),
(71021, 7010, '/uploads/images/seed/post/post-liquid-cat.jpg',  2),
(71022, 7010, '/uploads/images/seed/post/post-cat-box.jpg',     3);

-- 4.4 评论（含楼中楼回复；comment_count 与各帖评论行数一致）
DELETE FROM `post_comment` WHERE id BETWEEN 72101 AND 72160;
INSERT INTO `post_comment` (`id`, `post_id`, `user_id`, `parent_id`, `content`, `like_count`, `create_time`) VALUES
-- 7001（4 条）
(72101, 7001, 10002, NULL, '前后差好大！请问用的什么沐浴露，我家咪咪也快到换毛季了', 6, NOW() - INTERVAL 12 DAY + INTERVAL 3 HOUR),
(72102, 7001, 10003, NULL, '金毛吹完真的会蓬成狮子😂 我家七崽是蓬成面包', 11, NOW() - INTERVAL 12 DAY + INTERVAL 6 HOUR),
(72103, 7001, 10001, 72101, '店里用的宠物专用低敏香波，你可以到店让师傅看下适合猫的款', 3, NOW() - INTERVAL 12 DAY + INTERVAL 8 HOUR),
(72104, 7001, 10006, NULL, '豆豆这毛发状态也太好了吧', 2, NOW() - INTERVAL 11 DAY),
-- 7002（5 条）
(72105, 7002, 10005, NULL, '吐毛球频率这个变化太真实了，请问局部清理大概什么价位？', 4, NOW() - INTERVAL 9 DAY + INTERVAL 2 HOUR),
(72106, 7002, 10002, 72105, '我做的是猫咪造型美容那档 138，只做局部清理会便宜些', 5, NOW() - INTERVAL 9 DAY + INTERVAL 5 HOUR),
(72107, 7002, 10001, NULL, '英短洗完那次我家猫三天没理我，咪咪适应吗', 8, NOW() - INTERVAL 8 DAY),
(72108, 7002, 10002, 72107, '第一次也会躲，去了第三次开始进门就自己往台上走哈哈', 9, NOW() - INTERVAL 8 DAY + INTERVAL 4 HOUR),
(72109, 7002, 10004, NULL, '学到了，换毛季前去一次', 1, NOW() - INTERVAL 7 DAY),
-- 7003（6 条）
(72110, 7003, 10004, NULL, '收藏了，家里新猫下个月到，正好用上', 5, NOW() - INTERVAL 15 DAY + INTERVAL 6 HOUR),
(72111, 7003, 10006, NULL, '补一个：南方体外千万别停，去年冬天跳蚤给我上过一课', 14, NOW() - INTERVAL 14 DAY),
(72112, 7003, 10005, NULL, '请问幼猫第一次体外驱虫是几周做呀？', 2, NOW() - INTERVAL 14 DAY + INTERVAL 8 HOUR),
(72113, 7003, 10001, 72112, '医生和我说的是 8 周龄之后，太小的先别上滴剂', 6, NOW() - INTERVAL 14 DAY + INTERVAL 10 HOUR),
(72114, 7003, 10003, NULL, '内外同驱分段那个提醒太对了，我上次差点买错', 4, NOW() - INTERVAL 13 DAY),
(72115, 7003, 10002, NULL, '这个表比我搜到的都清楚，感谢整理', 7, NOW() - INTERVAL 13 DAY + INTERVAL 3 HOUR),
-- 7004（4 条）
(72116, 7004, 10001, NULL, '柯基 = 行走的蒲公英，同感落泪', 12, NOW() - INTERVAL 7 DAY + INTERVAL 2 HOUR),
(72117, 7004, 10004, NULL, '深层洗护真的管用吗？我家贵宾最近也在掉毛', 3, NOW() - INTERVAL 7 DAY + INTERVAL 5 HOUR),
(72118, 7004, 10003, 72117, '管两周左右，配合自己每天梳毛能撑更久', 6, NOW() - INTERVAL 7 DAY + INTERVAL 7 HOUR),
(72119, 7004, 10002, NULL, '笑死，腿完全看不见了', 9, NOW() - INTERVAL 6 DAY),
-- 7005（5 条）
(72120, 7005, 10003, NULL, '床垫开线哈哈哈哈不愧是二哈，建议宜家给你家打钱', 23, NOW() - INTERVAL 5 DAY + INTERVAL 1 HOUR),
(72121, 7005, 10001, NULL, '日间寄养放电这招学到了', 7, NOW() - INTERVAL 5 DAY + INTERVAL 4 HOUR),
(72122, 7005, 10006, 72121, '真的，回来路上就开始睡，到家直接关机', 10, NOW() - INTERVAL 5 DAY + INTERVAL 6 HOUR),
(72123, 7005, 10005, NULL, '棉花下雪的画面感太强了', 5, NOW() - INTERVAL 4 DAY),
(72124, 7005, 10004, NULL, '同款，我家拆的是沙发扶手，抱抱', 8, NOW() - INTERVAL 4 DAY + INTERVAL 3 HOUR),
-- 7006（6 条）
(72125, 7006, 10002, NULL, '图一图二对比也太大了，养得真好，功德+1', 15, NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR),
(72126, 7006, 10001, NULL, '一个多月就会自己吃粮了，壮实', 6, NOW() - INTERVAL 3 DAY + INTERVAL 5 HOUR),
(72127, 7006, 10005, 72125, '谢谢！主要它们俩也争气', 4, NOW() - INTERVAL 3 DAY + INTERVAL 8 HOUR),
(72128, 7006, 10003, NULL, '芝麻和汤圆，名字好可爱', 7, NOW() - INTERVAL 2 DAY),
(72129, 7006, 10006, NULL, '捡猫人上大分', 9, NOW() - INTERVAL 2 DAY + INTERVAL 4 HOUR),
(72130, 7006, 10004, NULL, '请问羊奶粉用的哪个牌子？', 2, NOW() - INTERVAL 1 DAY),
-- 7007（3 条）
(72131, 7007, 10002, NULL, '图二真的不是换了一只狗？', 13, NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR),
(72132, 7007, 10003, NULL, '贵宾修完就是明星，太可爱了', 6, NOW() - INTERVAL 2 DAY + INTERVAL 9 HOUR),
(72133, 7007, 10001, NULL, '张师傅手艺可以，下次带豆豆去修脚底毛', 4, NOW() - INTERVAL 1 DAY),
-- 7008（4 条）
(72134, 7008, 10005, NULL, '配料表那条很实用，马克', 8, NOW() - INTERVAL 6 DAY + INTERVAL 2 HOUR),
(72135, 7008, 10001, NULL, '吞拿鱼罐我家狗闻到也想吃', 11, NOW() - INTERVAL 6 DAY + INTERVAL 6 HOUR),
(72136, 7008, 10002, 72135, '狗子冷静，这是猫罐😂', 17, NOW() - INTERVAL 6 DAY + INTERVAL 7 HOUR),
(72137, 7008, 10004, NULL, '求个慕斯牌子，我家是舔食慢星人', 3, NOW() - INTERVAL 5 DAY),
-- 7009（3 条）
(72138, 7009, 10003, NULL, '柯基军团哈哈哈哈，被压着打的豆豆好惨', 10, NOW() - INTERVAL 1 DAY + INTERVAL 2 HOUR),
(72139, 7009, 10006, NULL, '工作日下午确实人少，学习了', 5, NOW() - INTERVAL 1 DAY + INTERVAL 5 HOUR),
(72140, 7009, 10005, NULL, '回家秒睡是运动量到位的证明', 6, NOW() - INTERVAL 22 HOUR),
-- 7010（4 条）
(72141, 7010, 10001, NULL, 'liquid cat 认证成功😂', 12, NOW() - INTERVAL 10 HOUR),
(72142, 7010, 10002, NULL, '咪咪的睡姿是麻花卷，下次拍来比一比', 8, NOW() - INTERVAL 7 HOUR),
(72143, 7010, 10003, NULL, '纸箱才是猫的本体，猫为啥都爱箱子', 6, NOW() - INTERVAL 5 HOUR),
(72144, 7010, 10004, NULL, '糊图才是养猫人的常态，懂', 9, NOW() - INTERVAL 2 HOUR);

-- 4.5 点赞与收藏（展示“已点赞/已收藏”状态；计数列为全量统计，不要求一致）
DELETE FROM `post_like` WHERE id BETWEEN 73101 AND 73130;
INSERT INTO `post_like` (`id`, `post_id`, `user_id`, `create_time`) VALUES
(73101, 7001, 10002, NOW() - INTERVAL 12 DAY + INTERVAL 3 HOUR),
(73102, 7001, 10003, NOW() - INTERVAL 12 DAY + INTERVAL 6 HOUR),
(73103, 7001, 10006, NOW() - INTERVAL 11 DAY),
(73104, 7002, 10001, NOW() - INTERVAL 9 DAY + INTERVAL 2 HOUR),
(73105, 7002, 10005, NOW() - INTERVAL 9 DAY + INTERVAL 6 HOUR),
(73106, 7003, 10001, NOW() - INTERVAL 15 DAY + INTERVAL 5 HOUR),
(73107, 7003, 10002, NOW() - INTERVAL 14 DAY),
(73108, 7003, 10003, NOW() - INTERVAL 14 DAY + INTERVAL 6 HOUR),
(73109, 7003, 10005, NOW() - INTERVAL 13 DAY),
(73110, 7003, 10006, NOW() - INTERVAL 13 DAY + INTERVAL 4 HOUR),
(73111, 7004, 10001, NOW() - INTERVAL 7 DAY + INTERVAL 2 HOUR),
(73112, 7004, 10002, NOW() - INTERVAL 6 DAY),
(73113, 7005, 10001, NOW() - INTERVAL 5 DAY + INTERVAL 1 HOUR),
(73114, 7005, 10003, NOW() - INTERVAL 5 DAY + INTERVAL 3 HOUR),
(73115, 7005, 10005, NOW() - INTERVAL 4 DAY),
(73116, 7006, 10002, NOW() - INTERVAL 3 DAY + INTERVAL 2 HOUR),
(73117, 7006, 10001, NOW() - INTERVAL 3 DAY + INTERVAL 6 HOUR),
(73118, 7006, 10003, NOW() - INTERVAL 2 DAY),
(73119, 7007, 10002, NOW() - INTERVAL 2 DAY + INTERVAL 3 HOUR),
(73120, 7007, 10003, NOW() - INTERVAL 1 DAY),
(73121, 7008, 10002, NOW() - INTERVAL 6 DAY + INTERVAL 2 HOUR),
(73122, 7008, 10005, NOW() - INTERVAL 5 DAY),
(73123, 7009, 10003, NOW() - INTERVAL 1 DAY + INTERVAL 2 HOUR),
(73124, 7010, 10002, NOW() - INTERVAL 7 HOUR),
(73125, 7010, 10005, NOW() - INTERVAL 4 HOUR);

DELETE FROM `post_favorite` WHERE id BETWEEN 74101 AND 74110;
INSERT INTO `post_favorite` (`id`, `post_id`, `user_id`, `create_time`) VALUES
(74101, 7003, 10002, NOW() - INTERVAL 14 DAY),
(74102, 7003, 10003, NOW() - INTERVAL 13 DAY),
(74103, 7003, 10005, NOW() - INTERVAL 12 DAY),
(74104, 7008, 10005, NOW() - INTERVAL 5 DAY),
(74105, 7006, 10002, NOW() - INTERVAL 2 DAY),
(74106, 7010, 10003, NOW() - INTERVAL 5 HOUR);
