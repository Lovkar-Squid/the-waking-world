"""Blender preview of a colossus grid produced by the mod (tools/java/GridDump.java).

Usage inside Blender (Python console or the Claude Blender bridge):
    exec(open(r'...\\colossus_blender.py').read()); preview(r'...\\beast_7_40.json', r'...\\out.png')

Builds one mesh of exposed cube faces (MC (x,y,z) -> Blender (x,-z,y)), colours parts by label
(1 torso, 2 head, 3/4 arms, 5/6 legs) and specials (7 core, 8 eye), renders with the scene camera.
"""
import bpy, bmesh, json, os

COLORS = {1: (0.45, 0.46, 0.44, 1), 2: (0.55, 0.53, 0.50, 1), 3: (0.40, 0.42, 0.42, 1), 4: (0.40, 0.42, 0.42, 1),
          5: (0.36, 0.37, 0.36, 1), 6: (0.36, 0.37, 0.36, 1), 7: (1.0, 0.35, 0.05, 1), 8: (1.0, 0.6, 0.1, 1)}
DIRS = [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]


def material(k):
    m = bpy.data.materials.get(f'ColossusMat{k}') or bpy.data.materials.new(f'ColossusMat{k}')
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    bsdf.inputs['Base Color'].default_value = COLORS[k]
    bsdf.inputs['Roughness'].default_value = 0.9
    if k >= 7:
        bsdf.inputs['Emission Color'].default_value = COLORS[k]
        bsdf.inputs['Emission Strength'].default_value = 4.0
    return m


def build(grid_json, name='Colossus'):
    g = json.load(open(grid_json))
    filled = {(x, y, z): (l, s) for x, y, z, l, s in g['cells']}
    for o in list(bpy.data.objects):
        if o.name == name:
            bpy.data.objects.remove(o, do_unlink=True)
    mesh = bpy.data.meshes.new(name + 'Mesh')
    bm = bmesh.new()
    keys = sorted(COLORS)
    for (x, y, z), (l, s) in filled.items():
        k = s if s else l
        x0, y0, z0 = x, -(z + 1), y
        for (dx, dy, dz) in DIRS:
            if (x + dx, y + dy, z + dz) in filled:
                continue
            bd = (dx, -dz, dy)
            c = lambda a, b, cc: (x0 + a, y0 + b, z0 + cc)
            if bd == (1, 0, 0):    vs = [c(1,0,0), c(1,1,0), c(1,1,1), c(1,0,1)]
            elif bd == (-1, 0, 0): vs = [c(0,0,0), c(0,0,1), c(0,1,1), c(0,1,0)]
            elif bd == (0, 1, 0):  vs = [c(0,1,0), c(0,1,1), c(1,1,1), c(1,1,0)]
            elif bd == (0, -1, 0): vs = [c(0,0,0), c(1,0,0), c(1,0,1), c(0,0,1)]
            elif bd == (0, 0, 1):  vs = [c(0,0,1), c(1,0,1), c(1,1,1), c(0,1,1)]
            else:                  vs = [c(0,0,0), c(0,1,0), c(1,1,0), c(1,0,0)]
            f = bm.faces.new([bm.verts.new(v) for v in vs])
            f.material_index = keys.index(k)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.001)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh); bm.free()
    obj = bpy.data.objects.new(name, mesh)
    for k in keys:
        obj.data.materials.append(material(k))
    bpy.context.scene.collection.objects.link(obj)
    return obj, len(filled)


def preview(grid_json, out_png):
    obj, n = build(grid_json)
    bpy.context.scene.render.filepath = out_png
    bpy.ops.render.render(write_still=True)
    return n
