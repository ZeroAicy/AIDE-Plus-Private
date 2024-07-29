package io.github.zeroaicy.aide.ui.services;

import androidx.annotation.Keep;
import com.aide.common.AppLog;
import com.aide.ui.ServiceContainer;
import com.aide.ui.AppPreferences;
import com.aide.ui.util.BuildGradle;
import com.aide.ui.util.FileSystem;
import com.aide.ui.util.MavenDependencyVersion;
import com.aide.ui.util.MavenMetadataXml;
import com.aide.ui.util.PomXml;
import com.aide.ui.util.ArtifactNode;
import io.github.zeroaicy.aide.extend.ZeroAicyExtensionInterface;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.zeroaicy.util.Log;
import android.text.TextUtils;
import com.aide.ui.util.BuildGradle.MavenDependency;

/**
 * 更新底包时，再优化，那时必须抽离出修改点，只保留底包对其引用的api
 */
public class ZeroAicyMavenService {
	public static final String TAG = "ZeroAicyMavenService";

	public static final int defaultDepth = 10;
	/**
	 * 静态方法
	 */
	// getMetadataUrl
	public static String getMetadataUrl(BuildGradle.RemoteRepository remoteRepository, BuildGradle.MavenDependency dependency) {
        return new StringBuilder(remoteRepository.repositorieURL)
			.append("/").append(dependency.groupId.replace('.', '/'))
			.append("/").append(dependency.artifactId)
			.append("/maven-metadata.xml").toString();
    }
	// getMetadataPath[返回metadata文件]
	public static String getMetadataPath(BuildGradle.RemoteRepository remoteRepository, BuildGradle.MavenDependency dependency) {
        try {
			// getDefaulRepositoriePath应该叫默认下载maven缓存路径
			return new StringBuilder(getDefaulRepositoriePath())
				.append("/").append(dependency.groupId.replace('.', '/'))
				.append("/").append(dependency.artifactId)
				.append("/maven-metadata.xml")
				.toString();
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	// 返回Artifact的url
	public static String getArtifactUrl(BuildGradle.RemoteRepository remoteRepository, BuildGradle.MavenDependency dependency, String version, String type) {

		StringBuilder stringBuilder = new StringBuilder(remoteRepository.repositorieURL)
			.append("/").append(dependency.groupId.replace('.', '/'))
			.append("/").append(dependency.artifactId)
			.append("/").append(version)
			.append("/").append(dependency.artifactId).append("-").append(version);

		String classifier = ArtifactNode.getClassifier(dependency);
		if (!".pom".equals(type) && classifier != null) {
			stringBuilder.append("-").append(classifier);
		}
		stringBuilder.append(type);

		return stringBuilder.toString();
    }
	// Artifact的在本地缓存中的路径
	public static String getArtifactPath(BuildGradle.RemoteRepository remoteRepository, BuildGradle.MavenDependency dependency, String version, String type) {

		StringBuilder stringBuilder = new StringBuilder(getDefaulRepositoriePath())
			.append("/").append(dependency.groupId.replace('.', '/'))
			.append("/").append(dependency.artifactId)
			.append("/").append(version)
			.append("/").append(dependency.artifactId).append("-").append(version);

		String classifier = ArtifactNode.getClassifier(dependency);
		if (!".pom".equals(type) && classifier != null) {
			stringBuilder.append("-").append(classifier);
		}
		stringBuilder.append(type);

		return stringBuilder.toString();
	}

	/**
	 * 默认下载maven仓库路径
	 */
	// DW() -> getDefaulRepositoriePath
    public static String getDefaulRepositoriePath() {
        String userM2Repositories = ZeroAicyExtensionInterface.getUserM2Repositories();
        if (userM2Repositories != null) {
            return userM2Repositories;
        }
        try {
            return FileSystem.yS() + "/.aide/maven";
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }


	// 返回本地缓存不存在的依赖
	public List<BuildGradle.MavenDependency> getNotExistsLocalCache(Map<String, String> flatRepoPathMap, BuildGradle.MavenDependency dep) {
        try {
            List<BuildGradle.MavenDependency> notExistsLocalCache = new ArrayList<>();
			// 装箱
			ArtifactNode artifactNode = ArtifactNode.pack(dep);
            getNotExistsLocalCache(flatRepoPathMap, artifactNode, notExistsLocalCache, defaultDepth);

            return notExistsLocalCache;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	// 递归查找依赖缓存，不存在时加入list
    private void getNotExistsLocalCache(Map<String, String> flatRepoPathMap, ArtifactNode curArtifactNode, List<BuildGradle.MavenDependency> dependencyList, int depth) {
        try {
			//从缓存仓库计算MavenDependency依赖路径[pom|jar|aar]
			// 从依赖管理器中获取ArtifactNode
			curArtifactNode = makeUpdateDep(curArtifactNode);
			// 解算路径
			String curArtifactNodePath = resolveMavenDepPath(flatRepoPathMap, curArtifactNode);

			if (curArtifactNodePath == null) {
                dependencyList.add(curArtifactNode);
				return;
			}

			if (depth <= 0) {
				return;				
			}

			String depPomPath = getDepPomPath(curArtifactNodePath);
			PomXml depPomXml = PomXml.empty.getConfiguration(depPomPath);
			for (ArtifactNode subArtifactNode : depPomXml.depManages) {
				// dependencyManagement只做版本控制
				makeUpdateDep(subArtifactNode);
				
			}
			curArtifactNode = makeUpdateDep(curArtifactNode);
			Set<String> exclusionSet = curArtifactNode.getExclusionSet();
			for (ArtifactNode subArtifactNode : depPomXml.deps) {
				// 过滤排除依赖
				if (exclusionSet.contains(subArtifactNode.getGroupIdArtifactId())) {
					continue;
				}
				if (!vy(subArtifactNode)) {
					getNotExistsLocalCache(flatRepoPathMap, subArtifactNode, dependencyList, depth - 1);
				}
			}
		}
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
			throw new Error(th);
		}
    }



	/**
	 * 共有api
	 */


	// 刷新maven缓存
    public void refreshMavenCache() {
        try {
            ServiceContainer.sy(ServiceContainer.gn(), "Refreshing...", new Runnable(){
					@Override
					public void run() {
						//重置依赖缓存映射
						resetDepPathMap();
						try {
							//删除 maven缓存
							FileSystem.VH(getDefaulRepositoriePath());
						}
						catch (Throwable e) {

						}
					}
				}, new Runnable(){
					@Override
					public void run() {
						ServiceContainer.getProjectService().reloadingProject();
					}
				});
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	//重置当前服务的依赖记录
	public void resetDepMap() {
		this.depManager.clear();
    }



	// 重置依赖在maven缓存路径中的映射
	// FH() -> resetDepPathMap
	@Keep
	public void resetDepPathMap() {
        try {
            this.depPathMapping.clear();
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	// 所有gradle文件中显示声明的依赖
	// 按照依赖顺序，依次传入
	// 这意味着，可以获得所有显示依赖
	// 解析MavenDependency的子依赖
	@Keep
	public void resolvingDependency(BuildGradle.MavenDependency dependency) {
        try {
			//解析并填充此依赖
            resolvingDependency(dependency, defaultDepth);
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	// 递归，耗时操作
	// 解析gradle中的显示声明
	private void resolvingDependency(BuildGradle.MavenDependency dep, int depth) {
        try {
			//从缓存仓库计算MavenDependency依赖路径[pom|jar|aar]
            String depPath = resolveMavenDepPath(null, dep);

			if (depPath == null) {
				// 没有此依赖未下载
				return;
			}

			// 当前依赖路径的实际版本
			{
				// 获取此依赖的缓存版本
				String version = getVersion(depPath);
				
				// 检查是否更新依赖版本
				makeUpdateDep(new ArtifactNode(dep, version));

			}

			// 限制递归层级
			if (depth < 1) {
				return;
			}

			String depPomPath = getDepPomPath(depPath);
			PomXml curPomXml = PomXml.empty.getConfiguration(depPomPath);
			for (ArtifactNode subArtifactNode : curPomXml.depManages) {
				// dependencyManagement只做版本控制
				makeUpdateDep(subArtifactNode);

			}

			ArtifactNode curArtifactNode = makeUpdateDep(dep);
			Set<String> exclusionSet = curArtifactNode.getExclusionSet();

			for (ArtifactNode subArtifactNode : curPomXml.deps) {
				if (exclusionSet.contains(subArtifactNode.getGroupIdArtifactId())) {
					continue;
				}
				if (!vy(subArtifactNode)) {
					resolvingDependency(subArtifactNode, depth - 1);
				}
			}

        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 返回此依赖及其子依赖在maven缓存仓库中的路径
	 * 编译时的依赖解析
	 */
	@Deprecated
	@Keep
	public List<String> resolveFullDependencyTree(Map<String, String> flatRepositoryPathMap, BuildGradle.MavenDependency dep) {
        try {
            return resolveFullDependencyTree(flatRepositoryPathMap, resolveMavenDepPath(flatRepositoryPathMap, makeUpdateDep(dep)));
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 返回当前依赖的maven缓存路径
	 * 但不会查找flatDir
	 */
	@Keep
	public String resolveMavenDepPath(BuildGradle.MavenDependency dependency) {
        try {
            return resolveMavenDepPath(null, dependency);
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 从给定的依赖路径，返回其自己及子依赖路径
	 * 递归3层
	 */
	@Deprecated
	@Keep
	public List<String> resolveFullDependencyTree(String depPath) {
        try {
            return resolveFullDependencyTree(null, depPath);
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }



	/**
	 * 此依赖是否存在本地缓存 hasMavenCache
	 */
	@Keep
	public boolean existsLocalMavenCache(Map<String, String> flatRepoPathMap, BuildGradle.MavenDependency dependency) {
        try {
            return resolveMavenDepPath(flatRepoPathMap, makeUpdateDep(dependency)) != null;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	/**
	 * 私有，内部实现 依赖管理器
	 */
    private final Map<String, ArtifactNode> depManager = new HashMap<>();

	// 依赖与此依赖本地仓库地址
	// 依赖与本地依赖缓存映射
    private final Map<ArtifactNode, String> depPathMapping = new HashMap<>();

	public static Map<ArtifactNode, String> getDepPathMapping(ZeroAicyMavenService mavenService) {
		return mavenService.depPathMapping;
	}


	/**
	 * 根据缓存仓库路径和依赖，解析依赖路径
	 */
	private String getArtifactCachePath2(String repositoriePath, BuildGradle.MavenDependency dependency) {
		String groupId = dependency.groupId;
		String artifactId = dependency.artifactId;
		String version = dependency.version;
		String packaging = dependency.packaging;

		String artifactIdDir = repositoriePath + "/" + groupId.replace(".", "/") + "/" + artifactId;
		if (!FileSystem.isDirectory(artifactIdDir)) {
			return null;
		}

		//搜索本地仓库已有的版本或清单
		String searchVersion = this.searchLocalDepVersion(artifactIdDir, version);
		if (searchVersion == null) {
            return null;
        }
		// 版本没有通配符且本地版本与解算的版本不一样
		if( !version.endsWith("+")
		   && !version.equals(searchVersion)){
			   // 强制使非使用通配符的版本与实际版本统一
			   // 否则将会导致需要的版本与实际版本不符
			return null;
		}
		// 使用本地已有版本或者清单
		// 按道理不应该这样，这会导致...
        version = searchVersion;

		String artifactIdVersionDir = artifactIdDir + "/" + version + "/" + artifactId + "-" + version;

		final String classifier = ArtifactNode.getClassifier(dependency);
		// 处理仅pom依赖 当classifier不为null不处理pom依赖
		if ("pom".equals(packaging) 
			&& classifier == null) {
			if (new File(artifactIdVersionDir + ".pom").isFile()) {
				return artifactIdVersionDir + ".pom";
			}
		}
		if (classifier != null) {
			artifactIdVersionDir += "-" + classifier;
		}

        if (new File(artifactIdVersionDir + ".jar").isFile()) {
            return artifactIdVersionDir + ".jar";
        }
        if (new File(artifactIdVersionDir + ".aar").isDirectory()) {
            return artifactIdVersionDir + ".aar";
        }
        if (new File(artifactIdVersionDir + ".exploded.aar").isDirectory()) {
            return artifactIdVersionDir + ".exploded.aar";
        }
        if (new File(artifactIdVersionDir + ".aar").isFile()) {
            extractedAar(artifactIdVersionDir + ".aar", artifactIdVersionDir + ".exploded.aar");
            return artifactIdVersionDir + ".exploded.aar";
        }
        return null;
	}
	/**
	 * 返回此依赖的 jar(jar) | aar(aar) | pom(bom) 文件路径
	 */
	//计算缓存路径
	private String getArtifactCachePath(String repositoriePath, String groupId, String artifactId, String version, String packaging) {
        String artifactIdDir = repositoriePath + "/" + groupId.replace(".", "/") + "/" + artifactId;

		if (!FileSystem.isDirectory(artifactIdDir)) {
			return null;
		}
		//搜索本地仓库已有的版本
		//按道理不应该这样
        String searchVersion = this.searchLocalDepVersion(artifactIdDir, version);
		if (searchVersion == null) {
            return null;
        }

		version = searchVersion;

        String artifactIdVersionDir = artifactIdDir + "/" + version + "/" + artifactId + "-" + version;
		if ("pom".equals(packaging)) {
			if (new File(artifactIdVersionDir + ".pom").isFile()) {
				return artifactIdVersionDir + ".pom";
			}
		}

        if (new File(artifactIdVersionDir + ".jar").isFile()) {
            return artifactIdVersionDir + ".jar";
        }
        if (new File(artifactIdVersionDir + ".aar").isDirectory()) {
            return artifactIdVersionDir + ".aar";
        }
        if (new File(artifactIdVersionDir + ".exploded.aar").isDirectory()) {
            return artifactIdVersionDir + ".exploded.aar";
        }
        if (new File(artifactIdVersionDir + ".aar").isFile()) {
            extractedAar(artifactIdVersionDir + ".aar", artifactIdVersionDir + ".exploded.aar");
            return artifactIdVersionDir + ".exploded.aar";
        }
        return null;
    }


	// 查询是否已有依赖或者版本控制
	// 即所有依赖引用都在这，方便更新版本
	/**
	 * 使用依赖管理器复用依赖
	 */
    private ArtifactNode makeUpdateDep(BuildGradle.MavenDependency dep) {
        try {
            ArtifactNode cache = this.depManager.get(dep.getGroupIdArtifactId());
			if (cache == null) {
				// 装箱
				cache = ArtifactNode.pack(dep);
				// 首次添加
				this.depManager.put(cache.getGroupIdArtifactId(), cache);

				return cache;
            }

			// 检查更新
			updateDep(ArtifactNode.pack(dep), cache);

			return cache;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	private void updateDep(ArtifactNode artifactNode, ArtifactNode cache) {
		if (artifactNode != cache) {

			if (artifactNode.version == null) {
				// 没有版本，最差的情况是依赖管理里存的也是
				return;
			}
			if( artifactNode.version.equals(cache.version)){
				this.depManager.put(artifactNode.getGroupIdArtifactId(), artifactNode);
			}
			//控制版本里的更新
			// 依赖管理里存的没有版本信息或者版本低
			if (cache.version == null 
				|| MavenDependencyVersion.compareTo(artifactNode.version, cache.version) > 0) {
				this.depManager.put(artifactNode.getGroupIdArtifactId(), artifactNode);
				// 
				//AppLog.d(TAG, "更新依赖版本", cache + " -> " + artifactNode.version);
			}
		}
	}

    private boolean P8(String str, String str2) {
        try {
			if (!new File(str2).isDirectory()) {
				return false;
			}
			File[] listFiles = new File(str2).listFiles();
			if (listFiles == null) {
				return true;
			}
			long lastModified = new File(str).lastModified();
			for (File file : listFiles) {
				if (file.isFile() && file.lastModified() < lastModified) {
					return false;
				}
			}
			return true;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }


    private void resolveFullDependencyTree(Map<String, String> flatRepositoryPathMap, String str, List<String> depPaths, int depth) {
        try {
            if (depPaths.contains(str)) {
                return;
            }
            depPaths.add(str);
			if (depth < 1) {
				return;
			}

			String depPomPath = getDepPomPath(str);

			PomXml curPomXml = PomXml.empty.getConfiguration(depPomPath);

			for (ArtifactNode artifactNode : curPomXml.depManages) {
				// dependencyManagement只做版本控制
				makeUpdateDep(artifactNode);
			}
			
			ArtifactNode curArtifactNode = makeUpdateDep(new ArtifactNode(curPomXml));
			Set<String> exclusionSet = curArtifactNode.getExclusionSet();

			for (ArtifactNode subArtifactNode : curPomXml.deps) {
				if (exclusionSet.contains(subArtifactNode.getGroupIdArtifactId())) {
					continue;
				}
				// 不知道为什么要这个
				if (vy(subArtifactNode)) {
					continue;
				}
				// 计算dependency的地址
				String depPath = resolveMavenDepPath(flatRepositoryPathMap, makeUpdateDep(subArtifactNode));

				if (depPath != null) {
					resolveFullDependencyTree(flatRepositoryPathMap, depPath, depPaths, depth - 1);
				}
			}
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }



    private void extractedAar(String aarPath, String outDir) {
        try {
            if (P8(aarPath, outDir)) {
                return;
            }
            try {
                FileSystem.u7(new FileInputStream(aarPath), outDir, true);
                AppLog.DW("Extracted AAR " + aarPath);
            }
			catch (IOException e) {
                e.printStackTrace();
            }
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 编译时
	 */
    private List<String> resolveFullDependencyTree(Map<String, String> flatRepositoryPathMap, String depPath) {
        try {
            ArrayList<String> arrayList = new ArrayList<>();
            if (depPath != null) {
                resolveFullDependencyTree(flatRepositoryPathMap, depPath, arrayList, defaultDepth);
            }
            return arrayList;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }


	//获取此依赖缓存路径的版本
    private static String getVersion(String str) {
        try {
            String[] split = str.split("/");
            return split[split.length - 2];
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }



	//从 jar|aar依赖路径返回pom路径
	// 对于有 classifier的并不准确
	/**
	 * 
	 */
    private String getDepPomPath(String depPath) {
        try {

			// 按道理应该是版本
			depPath = FileSystem.getParent(depPath);
			String versionDir = FileSystem.getName(depPath);
            if (depPath.endsWith(".exploded.aar")) {
				// 此时一定为版本目录
				depPath = FileSystem.getParent(versionDir);
				versionDir = FileSystem.getName(depPath);
            }
			String artifactIdDir = FileSystem.getName(FileSystem.getParent(depPath));
			return depPath + "/" + artifactIdDir + "-" + versionDir + ".pom";
		}
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }


    private String getMavenDependencyPath(BuildGradle.MavenDependency dependency) {
        try {
            for (String repositoriePath : getRepositoriePaths()) {
                String depPath = getArtifactCachePath2(repositoriePath, dependency);
				//getArtifactCachePath(repositoriePath, dependency.groupId, dependency.artifactId, dependency.version, dependency.packaging);
                if (depPath != null) {
                    return depPath;
                }
            }
            return null;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

    private List<String> getRepositoriePaths() {
        try {
            ArrayList<String> arrayList = new ArrayList<>();
            for (String str : AppPreferences.br().split(";")) {
                if (!str.trim().isEmpty()) {
                    arrayList.add(str.trim());
                }
            }
            arrayList.add(getDefaulRepositoriePath());
            return arrayList;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }
	private static MavenMetadataXml mavenMetadataXml = new MavenMetadataXml();

    private String searchLocalDepVersion(String artifactIdDir, String searchVersion) {
        try {
            String metadataPath = artifactIdDir + "/maven-metadata.xml";

			String version;
			if (!FileSystem.isFileAndNotZip(metadataPath) 
				|| (version = mavenMetadataXml.getConfiguration(metadataPath).getVersion(searchVersion)) == null) {
                try {
					//一但metadata找不到查找的版本
					//则 ls metadata同级目录
					//本地所有版本集合
                    ArrayList<String> versions = new ArrayList<>();
                    for (String artifactIdVersionDir : FileSystem.we(artifactIdDir)) {
                        versions.add(FileSystem.getName(artifactIdVersionDir));
                    }
                    return MavenMetadataXml.getVersion(versions, searchVersion);
                }
				catch (Exception unused) {
                    return null;
                }
            }
            return version;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 计算 flat仓库的aar[groupId无效]
	 */
    private String getFlatArtifactPath(String flatRepositoryPath, String groupId, String artifactId, String version) {
        try {
			//从
            String artifactPath = flatRepositoryPath + "/" + artifactId + ".aar";
            if (new File(artifactPath).exists()) {
                return artifactPath;
            }
            File[] listFiles = new File(flatRepositoryPath).listFiles();
            if (listFiles == null) {
				return null;
			}
			for (File file : listFiles) {
				String name = file.getName();

				if (name.startsWith(artifactId + "-") 
					&& name.endsWith(".aar")
				//是否匹配版本
					&& MavenMetadataXml.matchVersion(name.substring(artifactId.length() + 1, name.length() - 4), version)) {
					return file.getPath();
				}
			}
			return null;

        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }




    private boolean vy(BuildGradle.MavenDependency dependency) {
        try {
            return dependency.artifactId.contains("android-all");
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

	/**
	 * 计算依赖在maven缓存的路径
	 */
    private String resolveMavenDepPath(Map<String, String> flatRepoPathMap, BuildGradle.MavenDependency dep) {
        try {
            if (flatRepoPathMap != null) {
				// 从flat查找
                for (String flatRepositoryPath : flatRepoPathMap.keySet()) {
                    String flatArtifactPath = getFlatArtifactPath(flatRepositoryPath, dep.groupId, dep.artifactId, dep.version);
					if (flatArtifactPath != null) {
                        String name = new File(flatArtifactPath).getName();
                        String flatRepoCachePath = flatRepoPathMap.get(flatRepositoryPath);
						String explodedAarPath = flatRepoCachePath + "/" + name.substring(0, name.length() - 4) + ".exploded.aar";
                        //提取aar并解压
						extractedAar(flatArtifactPath, explodedAarPath);
                        return explodedAarPath;
                    }
                }
            }
			// 使用依赖管理器的
			ArtifactNode artifactNode = makeUpdateDep(dep);

			if (this.depPathMapping.containsKey(artifactNode)) {
                String depPath = this.depPathMapping.get(artifactNode);
                if (TextUtils.isEmpty(depPath)) {
				    return null;
                }
				return depPath;
            }

            String depPath = getMavenDependencyPath(artifactNode);
			this.depPathMapping.put(artifactNode, depPath);

            //AppLog.d(TAG, "解算依赖路径", artifactNode + " -> " + depPath);

			return depPath;
        }
		catch (Error th) {
			throw th;
		}
		catch (Throwable th) {
            throw new Error(th);
        }
    }

}
