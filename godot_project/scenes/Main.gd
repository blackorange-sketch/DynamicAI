extends Node3D

func _ready():
    var gltf = GLTFDocument.new()
    var state = GLTFState.new()
    var err = gltf.append_from_file("res://assets/model.glb", state)
    
    if err == OK:
        var scene = gltf.generate_scene(state)
        scene.rotation_degrees.y = 180  # <--- РОЗВЕРТАЄМО МОДЕЛЬ ДО НАС
        add_child(scene)
        print("3D Model Loaded Successfully via GLTFDocument!")
    else:
        print("Failed to load 3D model! Error code: ", err)
        
    var camera = Camera3D.new()
    camera.position = Vector3(0, 1.2, 3.5) # Трохи піднімаємо камеру до рівня обличчя
    add_child(camera)
    
    var light = DirectionalLight3D.new()
    light.rotation_degrees = Vector3(-45, 30, 0)
    add_child(light)

func play_animation(anim_name: String):
    print("Playing animation: ", anim_name)
