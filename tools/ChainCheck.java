import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 离线 bbmodel 链对拍工具（无 MC 依赖，纯数学）。
 *
 * 用法: java -cp .;gson.jar ChainCheck <bbmodel> [modelYOffset]
 *
 * 复刻两条链构建算法：
 *   A = 4.0 上游 (BlockbenchOutline.add sibling-relative + GroupTransformations.create 差分)
 *   B = mtr-next BbModelTree 当前实现
 * 对每个元素，计算它在模型空间的最终位置（pivot 累乘 + cube 偏移），并打印对比。
 *
 * 判据：一个「正确」的实现应该让所有元素的模型空间坐标都落在车厢范围
 *   x ±2 格 / y 0.6..4.8 格 / z ±9 格 （典型 17m 车厢）
 */
public final class ChainCheck {

	// ==========================================================
	// 链节（共同结构）
	// ==========================================================
	static final class Link {
		float px, py, pz;       // pivot (MC 空间, 像素)
		float rx, ry, rz;       // rotation (弧度)
		Link(float px, float py, float pz, float rx, float ry, float rz) {
			this.px = px; this.py = py; this.pz = pz;
			this.rx = rx; this.ry = ry; this.rz = rz;
		}
	}

	static final class Elem {
		String uuid, name, topGroup;
		List<Link> chain = new ArrayList<>(); // 从锚点（不含）到元素节（含）的链
		float cubeX, cubeY, cubeZ;            // cube 相对元素节的偏移（像素）
		float sizeX, sizeY, sizeZ;
	}

	// ==========================================================
	// 4.0 上游算法（A）
	// ==========================================================
	static final class Up {
		// sibling-relative, 与上游 BlockbenchOutline.add 一致
		static List<Link> outlineAdd(List<Link> parent, JsonObject prevSibling, JsonObject group) {
			JsonArray origin = arr(group, "origin");
			JsonArray rot = arr(group, "rotation");
			float ox, oy, oz; float rx, ry, rz;
			if (prevSibling == null) {
				ox = f(origin, 0); oy = f(origin, 1); oz = f(origin, 2);
				rx = ry = rz = 0;
			} else {
				JsonArray po = arr(prevSibling, "origin");
				ox = f(origin,0) - f(po,0);
				oy = f(origin,1) - f(po,1);
				oz = f(origin,2) - f(po,2);
				rx = (float) Math.toRadians(-f(rot,0));
				ry = (float) Math.toRadians(-f(rot,1));
				rz = (float) Math.toRadians(f(rot,2));
			}
			List<Link> out = new ArrayList<>(parent);
			out.add(new Link(-ox, -oy, oz, rx, ry, rz));
			return out;
		}

		// 模拟 GroupTransformations.create + BlockbenchElement.setModelPart
		static Elem element(List<Link> groupChain, JsonObject el, float modelYOffset) {
			JsonArray o = arr(el, "origin");
			JsonArray rot = arr(el, "rotation");
			List<Link> chain = new ArrayList<>(groupChain);
			chain.add(new Link(-f(o,0), -f(o,1), f(o,2),
					(float)Math.toRadians(-f(rot,0)),
					(float)Math.toRadians(-f(rot,1)),
					(float)Math.toRadians(f(rot,2))));
			// create：差分 + offset 只在第一节
			float cx=0, cy=0, cz=0, offset = modelYOffset*16;
			List<Link> built = new ArrayList<>();
			for (Link l : chain) {
				built.add(new Link(l.px-cx, l.py-cy-offset, l.pz-cz, l.rx, l.ry, l.rz));
				cx += l.px; cy += l.py; cz += l.pz;
				offset = 0;
			}
			// BlockbenchElement 坐标
			float originX = -f(o,0);
			float originY = -f(o,1) - modelYOffset*16;
			float originZ = f(o,2);
			float x = -f(arr(el,"to"),0) - originX;
			float y = -f(arr(el,"to"),1) - originY - modelYOffset*16;
			float z = f(arr(el,"from"),2) - originZ;
			Elem e = new Elem();
			e.uuid = str(el,"uuid","");
			e.name = str(el,"name","");
			e.chain = built;
			e.cubeX = x; e.cubeY = y; e.cubeZ = z;
			e.sizeX = (float)Math.round(f(arr(el,"to"),0)-f(arr(el,"from"),0));
			e.sizeY = (float)Math.round(f(arr(el,"to"),1)-f(arr(el,"from"),1));
			e.sizeZ = (float)Math.round(f(arr(el,"to"),2)-f(arr(el,"from"),2));
			return e;
		}

		// 上游 iterateChildren
		static void iterate(JsonObject outline, JsonObject prevOutline, List<Link> parentChain,
				Map<String, JsonObject> uuidToEl, String topName, List<Elem> out, float myo) {
			List<Link> myChain = outlineAdd(parentChain, prevOutline, outline);
			if (outline.has("children") && outline.get("children").isJsonArray()) {
				// 上游：childrenUuid 里的 uuid 元素先处理（用 myChain），然后递归子组
				List<JsonObject> childGroups = new ArrayList<>();
				List<String> childUuids = new ArrayList<>();
				for (JsonElement c : outline.getAsJsonArray("children")) {
					if (c.isJsonPrimitive()) childUuids.add(c.getAsString());
					else if (c.isJsonObject()) childGroups.add(c.getAsJsonObject());
				}
				// 元素
				for (String u : childUuids) {
					JsonObject el = uuidToEl.remove(u);
					if (el != null) {
						Elem e = element(myChain, el, myo);
						e.topGroup = topName;
						out.add(e);
					}
				}
				// 子组（上游传的是 parent 的 gt，且 previous=当前 outline）
				JsonObject prev = null;
				for (JsonObject cg : childGroups) {
					iterate(cg, prev, parentChain, uuidToEl, topName, out, myo);
					prev = cg;
				}
			}
		}
	}

	// ==========================================================
	// mtr-next 算法（B）—— 按 BbModelTree.java 实际实现复刻
	// ==========================================================
	static final class Mine {
		// BbModelTree.outlineAdd: previousSibling==null → 绝对 origin、rotation 归零
		// 否则 origin - prevSibling.origin（注意：传进来的 prevSibling 是什么由调用方决定）
		static List<Link> outlineAdd(List<Link> parent, JsonObject prevSibling, JsonObject group) {
			JsonArray origin = arr(group, "origin");
			JsonArray rot = arr(group, "rotation");
			float ox, oy, oz, rx, ry, rz;
			if (prevSibling == null) {
				ox = f(origin, 0); oy = f(origin, 1); oz = f(origin, 2);
				rx = ry = rz = 0;
			} else {
				JsonArray po = arr(prevSibling, "origin");
				ox = f(origin,0) - f(po,0);
				oy = f(origin,1) - f(po,1);
				oz = f(origin,2) - f(po,2);
				rx = (float) Math.toRadians(-f(rot,0));
				ry = (float) Math.toRadians(-f(rot,1));
				rz = (float) Math.toRadians(f(rot,2));
			}
			List<Link> out = new ArrayList<>(parent);
			out.add(new Link(-ox, -oy, oz, rx, ry, rz));
			return out;
		}

		// 修复后（对齐上游字节码）：递归传 previous=当前组, GT=入参(parentChain,不含当前组)
		static void iterate(JsonObject outline, JsonObject prevSibling, List<Link> parentChain,
				Map<String, JsonObject> uuidToEl, String topName, List<Elem> out, float myo) {
			List<Link> myChain = outlineAdd(parentChain, prevSibling, outline);
			if (outline.has("children") && outline.get("children").isJsonArray()) {
				final JsonObject currentGroup = outline;
				for (final JsonElement c : outline.getAsJsonArray("children")) {
					if (c.isJsonPrimitive()) {
						JsonObject el = uuidToEl.remove(c.getAsString());
						if (el != null) {
							Elem e = Up.element(myChain, el, myo);
							e.topGroup = topName;
							out.add(e);
						}
					} else if (c.isJsonObject()) {
						JsonObject cg = c.getAsJsonObject();
						iterate(cg, currentGroup, parentChain, uuidToEl, topName, out, myo);
					}
				}
			}
		}
	}

	// ==========================================================
	// 上游字节码核验过的真实算法（A'）：
	//   javap 确认 lambda$iterateChildren$12 = iterateChildren(child, 父组, id, 入参GT, consumer)
	//   ⇒ 嵌套组的 previous = 父组（兄弟语义在【顶层多组】间，不在嵌套层）
	//   ⇒ 子组的基准 GT = 入参 GT（不含当前组的变换节点）
	// ==========================================================
	static final class UpReal {
		static void iterate(JsonObject outline, JsonObject prevOutline, List<Link> parentChain,
				Map<String, JsonObject> uuidToEl, String topName, List<Elem> out, float myo) {
			List<Link> myChain = Up.outlineAdd(parentChain, prevOutline, outline);
			if (outline.has("children") && outline.get("children").isJsonArray()) {
				for (JsonElement c : outline.getAsJsonArray("children")) {
					if (c.isJsonPrimitive()) {
						JsonObject el = uuidToEl.remove(c.getAsString());
						if (el != null) {
							Elem e = Up.element(myChain, el, myo);
							e.topGroup = topName;
							out.add(e);
						}
					} else if (c.isJsonObject()) {
						JsonObject cg = c.getAsJsonObject();
						// ★ 上游：递归传 (cg, 本组, 入参GT) —— previous=父组、GT 不含本组
						iterate(cg, outline, parentChain, uuidToEl, topName, out, myo);
					}
				}
			}
		}
	}

	// ==========================================================
	// 数学：链 → 元素中心的世界位置（无旋转，只平移累乘）
	// ==========================================================
	static float[] world(List<Link> chain, Elem e, boolean withRot) {
		// 从锚点开始，应用每节的 translateAndRotate
		// ModelPart.translateAndRotate: translate(pivot/16) 然后旋转
		// 简化（无旋转或旋转为 0 时）：只累加 pivot
		// 有旋转时用 4x4 矩阵（简化：我们只关心平移；旋转对「是否在车厢内」影响小）
		float x = 0, y = 0, z = 0;
		if (withRot) {
			// 用齐次矩阵
			double[] m = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}; // row-major 4x4
			for (Link l : chain) {
				m = mul(m, translate(l.px, l.py, l.pz));
				m = mul(m, rotZ(l.rz));
				m = mul(m, rotY(l.ry));
				m = mul(m, rotX(l.rx));
			}
			m = mul(m, translate(e.cubeX + e.sizeX/2, e.cubeY + e.sizeY/2, e.cubeZ + e.sizeZ/2));
			return new float[]{(float)m[3]/16f, (float)m[7]/16f, (float)m[11]/16f};
		} else {
			for (Link l : chain) { x += l.px; y += l.py; z += l.pz; }
			return new float[]{(x + e.cubeX + e.sizeX/2)/16f,
					(y + e.cubeY + e.sizeY/2)/16f,
					(z + e.cubeZ + e.sizeZ/2)/16f};
		}
	}

	static double[] mul(double[] a, double[] b) {
		double[] r = new double[16];
		for (int i=0;i<4;i++) for (int j=0;j<4;j++) {
			double s = 0;
			for (int k=0;k<4;k++) s += a[i*4+k] * b[k*4+j];
			r[i*4+j] = s;
		}
		return r;
	}
	static double[] translate(double x, double y, double z) {
		return new double[]{1,0,0,x, 0,1,0,y, 0,0,1,z, 0,0,0,1};
	}
	static double[] rotX(double a) {
		double c=Math.cos(a), s=Math.sin(a);
		return new double[]{1,0,0,0, 0,c,-s,0, 0,s,c,0, 0,0,0,1};
	}
	static double[] rotY(double a) {
		double c=Math.cos(a), s=Math.sin(a);
		return new double[]{c,0,s,0, 0,1,0,0, -s,0,c,0, 0,0,0,1};
	}
	static double[] rotZ(double a) {
		double c=Math.cos(a), s=Math.sin(a);
		return new double[]{c,-s,0,0, s,c,0,0, 0,0,1,0, 0,0,0,1};
	}

	// ==========================================================
	// main
	// ==========================================================
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("用法: java ChainCheck <bbmodel> [modelYOffset]");
			return;
		}
		Path path = Path.of(args[0]);
		float myo = args.length > 1 ? Float.parseFloat(args[1]) : 1.0F;
		String json = Files.readString(path, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();

		// 5.0 格式迁移（同 BbModelTree.topLevelGroups）
		Map<String, JsonObject> groupsByUuid = new HashMap<>();
		if (root.has("groups") && root.get("groups").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("groups")) {
				if (e.isJsonObject()) {
					String u = str(e.getAsJsonObject(),"uuid","");
					if (!u.isEmpty()) groupsByUuid.put(u, e.getAsJsonObject());
				}
			}
		}
		List<JsonObject> topGroups = new ArrayList<>();
		if (root.has("outliner") && root.get("outliner").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("outliner")) {
				if (e.isJsonObject()) topGroups.add(resolveNode(e.getAsJsonObject(), groupsByUuid));
			}
		}

		Map<String, JsonObject> uuidToEl = new HashMap<>();
		if (root.has("elements") && root.get("elements").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("elements")) {
				if (e.isJsonObject()) {
					JsonObject o = e.getAsJsonObject();
					String u = str(o,"uuid","");
					if (!u.isEmpty()) uuidToEl.put(u, o);
				}
			}
		}
		int totalElements = uuidToEl.size();

		// 跑两条算法（各自独立的 uuidToEl 副本）
		List<Elem> upElems = new ArrayList<>();
		Map<String, JsonObject> upMap = new HashMap<>(uuidToEl);
		for (JsonObject g : topGroups) {
			UpReal.iterate(g, null, new ArrayList<>(), upMap, str(g,"name",""), upElems, myo);
		}
		List<Elem> myElems = new ArrayList<>();
		Map<String, JsonObject> myMap = new HashMap<>(uuidToEl);
		for (JsonObject g : topGroups) {
			Mine.iterate(g, null, new ArrayList<>(), myMap, str(g,"name",""), myElems, myo);
		}

		System.out.println("=== " + path.getFileName() + " ===");
		System.out.println("bbmodel 元素总数 = " + totalElements);
		System.out.println("上游算法解析出 = " + upElems.size());
		System.out.println("本项目算法解析出 = " + myElems.size());

		// 判断标准：元素中心是否落在「合理车厢」范围
		//   x ±2 格 / y -1..4.8 格（y 向下为负所以会看到负值）/ z ±9 格（17m 车）
		System.out.println();
		System.out.println("=== 包围盒（模型空间，格）===");
		printBBox("上游", upElems);
		printBBox("本项目", myElems);

		// 逐元素对差
		System.out.println();
		System.out.println("=== 两算法差 > 0.05 格的元素 ===");
		Map<String, Elem> upByUuid = new LinkedHashMap<>();
		for (Elem e : upElems) upByUuid.put(e.uuid, e);
		int bad = 0; float worst = 0; String worstLabel = "";
		for (Elem me : myElems) {
			Elem ue = upByUuid.get(me.uuid);
			if (ue == null) { System.out.println("  本项目独有: " + me.topGroup + "/" + me.name); continue; }
			float[] uw = world(ue.chain, ue, true);
			float[] mw = world(me.chain, me, true);
			float dx = mw[0]-uw[0], dy = mw[1]-uw[1], dz = mw[2]-uw[2];
			float mag = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
			if (mag > worst) { worst = mag; worstLabel = me.topGroup + "/" + me.name; }
			if (mag > 0.05f) {
				bad++;
				if (bad <= 10) {
					System.out.println(String.format("  %s/%s  上=(%.2f,%.2f,%.2f) 本=(%.2f,%.2f,%.2f) Δ=(%.3f,%.3f,%.3f)",
							me.topGroup, me.name, uw[0],uw[1],uw[2], mw[0],mw[1],mw[2], dx,dy,dz));
				}
			}
		}
		System.out.println("  差异元素 = " + bad + " / " + myElems.size() + "  最大 = " + String.format("%.3f", worst) + " (" + worstLabel + ")");

		// 扶手等典型部件的具体位置（hand 组）
		System.out.println();
		System.out.println("=== 「hand」组元素的模型位置（前 8 个）===");
		int n = 0;
		for (Elem e : upElems) {
			if (!e.topGroup.toLowerCase().contains("hand")) continue;
			if (n++ >= 8) break;
			float[] w = world(e.chain, e, true);
			System.out.println(String.format("  上游 %s/%s -> (%.2f, %.2f, %.2f)", e.topGroup, e.name, w[0], w[1], w[2]));
		}
		n = 0;
		for (Elem e : myElems) {
			if (!e.topGroup.toLowerCase().contains("hand")) continue;
			if (n++ >= 8) break;
			float[] w = world(e.chain, e, true);
			System.out.println(String.format("  本项 %s/%s -> (%.2f, %.2f, %.2f)", e.topGroup, e.name, w[0], w[1], w[2]));
		}
	}

	static void printBBox(String label, List<Elem> elems) {
		float lox=Float.MAX_VALUE, loy=Float.MAX_VALUE, loz=Float.MAX_VALUE;
		float hix=-Float.MAX_VALUE, hiy=-Float.MAX_VALUE, hiz=-Float.MAX_VALUE;
		for (Elem e : elems) {
			float[] w = world(e.chain, e, true);
			lox = Math.min(lox, w[0]); loy = Math.min(loy, w[1]); loz = Math.min(loz, w[2]);
			hix = Math.max(hix, w[0]); hiy = Math.max(hiy, w[1]); hiz = Math.max(hiz, w[2]);
		}
		System.out.println(String.format("  %s: n=%d x[%.2f..%.2f] y[%.2f..%.2f] z[%.2f..%.2f]",
				label, elems.size(), lox, hix, loy, hiy, loz, hiz));
	}

	static List<JsonObject> dummy() { return null; }

	static JsonObject resolveNode(JsonObject node, Map<String, JsonObject> byUuid) {
		String u = str(node, "uuid", "");
		JsonObject def = u.isEmpty() ? null : byUuid.get(u);
		JsonObject merged = def == null ? node : def.deepCopy();
		if (node.has("children") && node.get("children").isJsonArray()) {
			JsonArray kids = new JsonArray();
			for (JsonElement c : node.getAsJsonArray("children")) {
				kids.add(c.isJsonObject() ? resolveNode(c.getAsJsonObject(), byUuid) : c);
			}
			merged.add("children", kids);
		}
		return merged;
	}

	static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}
	static float f(JsonArray a, int i) {
		return a != null && a.size() > i && a.get(i).isJsonPrimitive() ? a.get(i).getAsFloat() : 0;
	}
	static String str(JsonObject o, String k, String d) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d;
	}
}
