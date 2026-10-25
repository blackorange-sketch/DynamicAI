extends Node3D

var model_instance: Node3D

func _ready():
    var vrm_scene = load("res://assets/anime_girl.glb")
    if vrm_scene:
        model_instance = vrm_scene.instantiate()
        add_child(model_instance)
        print("VRM Model Loaded Successfully!")
        
    var camera = Camera3D.new()
    camera.position = Vector3(0, 1.0, 3.5)
    add_child(camera)
    
    var light = DirectionalLight3D.new()
    light.rotation_degrees = Vector3(-45, 30, 0)
    add_child(light)

func play_animation(anim_name: String):
    print("Playing animation: ", anim_name)
