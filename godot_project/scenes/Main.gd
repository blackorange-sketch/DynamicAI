extends Node3D

func _ready():
    # Створюємо тестовий куб замість моделі
    var mesh_instance = MeshInstance3D.new()
    var box_mesh = BoxMesh.new()
    mesh_instance.mesh = box_mesh
    add_child(mesh_instance)
    
    # Додаємо камеру
    var camera = Camera3D.new()
    camera.position = Vector3(0, 1.0, 3.5)
    add_child(camera)
    
    # Додаємо світло
    var light = DirectionalLight3D.new()
    light.rotation_degrees = Vector3(-45, 30, 0)
    add_child(light)

func play_animation(anim_name: String):
    print("Playing animation: ", anim_name)
