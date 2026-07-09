-- ============================================================================
-- V2 · Seeds obligatorios de la Fase 1 (SPEC §10): banco inicial de frases
-- motivadoras y preguntas de trivia, ambos bilingües (ES/EN). Sin ellos, /frase y
-- /trivia nacerían vacíos. Se cargan como migración de datos (no se tocan a mano).
--
-- Cantidades: 32 frases (≥30 exigidas) y 50 preguntas de trivia (≥50 exigidas).
-- Cada fila trae las dos versiones de idioma para servir según el idioma del usuario.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Frases motivadoras (categoría MOTIVACION por defecto). autor NULL = frase propia/anónima.
-- ----------------------------------------------------------------------------
INSERT INTO frases (texto_es, texto_en, autor, categoria) VALUES
('No cuentes los días, haz que los días cuenten.', 'Do not count the days, make the days count.', 'Muhammad Ali', 'MOTIVACION'),
('El único mal entrenamiento es el que no haces.', 'The only bad workout is the one you did not do.', NULL, 'MOTIVACION'),
('La disciplina es elegir entre lo que quieres ahora y lo que quieres más.', 'Discipline is choosing between what you want now and what you want most.', NULL, 'MOTIVACION'),
('Tu cuerpo aguanta casi todo; es a tu mente a la que hay que convencer.', 'Your body can stand almost anything; it is your mind you have to convince.', NULL, 'MOTIVACION'),
('El sudor de hoy es la sonrisa de mañana.', 'Today''s sweat is tomorrow''s smile.', NULL, 'MOTIVACION'),
('Fuerte no es un tamaño, es una actitud.', 'Strong is not a size, it is an attitude.', NULL, 'MOTIVACION'),
('Cada repetición te acerca a tu mejor versión.', 'Every rep brings you closer to your best self.', NULL, 'MOTIVACION'),
('No pares cuando estés cansado, para cuando hayas terminado.', 'Do not stop when you are tired, stop when you are done.', NULL, 'MOTIVACION'),
('Progreso, no perfección.', 'Progress, not perfection.', NULL, 'MOTIVACION'),
('Los resultados llegan a quien no abandona.', 'Results come to those who do not quit.', NULL, 'MOTIVACION'),
('Entrena aunque nadie te mire; esfuérzate igual.', 'Train like nobody is watching; give it your all anyway.', NULL, 'MOTIVACION'),
('Un poco cada día suma mucho al final.', 'A little every day adds up to a lot.', NULL, 'MOTIVACION'),
('La motivación te arranca, el hábito te mantiene.', 'Motivation gets you started, habit keeps you going.', NULL, 'MOTIVACION'),
('No busques excusas, busca resultados.', 'Do not look for excuses, look for results.', NULL, 'MOTIVACION'),
('El gimnasio no es un castigo, es tu tiempo.', 'The gym is not a punishment, it is your time.', NULL, 'MOTIVACION'),
('Duele ahora o duele después: elige.', 'It hurts now or it hurts later: choose.', NULL, 'MOTIVACION'),
('Sé más fuerte que tu mejor excusa.', 'Be stronger than your best excuse.', NULL, 'MOTIVACION'),
('Cae siete veces, levántate ocho.', 'Fall seven times, stand up eight.', 'Proverbio japonés', 'MOTIVACION'),
('La constancia vence al talento cuando el talento no es constante.', 'Consistency beats talent when talent is not consistent.', NULL, 'MOTIVACION'),
('Hazlo por la persona en la que te vas a convertir.', 'Do it for the person you are becoming.', NULL, 'MOTIVACION'),
('El descanso también entrena.', 'Rest is part of the training too.', NULL, 'MOTIVACION'),
('Menos quejas, más repeticiones.', 'Fewer complaints, more reps.', NULL, 'MOTIVACION'),
('Tu única competencia eres tú de ayer.', 'Your only competition is you from yesterday.', NULL, 'MOTIVACION'),
('Sueña en grande, entrena más grande.', 'Dream big, train bigger.', NULL, 'MOTIVACION'),
('El esfuerzo de hoy es la fuerza de mañana.', 'Today''s effort is tomorrow''s strength.', NULL, 'MOTIVACION'),
('No tienes que ser extremo, solo constante.', 'You do not have to be extreme, just consistent.', NULL, 'MOTIVACION'),
('Cada gota de sudor cuenta.', 'Every drop of sweat counts.', NULL, 'MOTIVACION'),
('Empieza donde estás, usa lo que tienes, haz lo que puedas.', 'Start where you are, use what you have, do what you can.', 'Arthur Ashe', 'MOTIVACION'),
('La energía que inviertes vuelve multiplicada.', 'The energy you invest comes back multiplied.', NULL, 'MOTIVACION'),
('No hay ascensor al éxito, hay que subir por las escaleras.', 'There is no elevator to success, you have to take the stairs.', NULL, 'MOTIVACION'),
('Convierte el "no puedo" en una repetición más.', 'Turn "I cannot" into one more rep.', NULL, 'MOTIVACION'),
('El cuerpo logra lo que la mente cree.', 'The body achieves what the mind believes.', NULL, 'MOTIVACION');

-- ----------------------------------------------------------------------------
-- Preguntas de trivia (25 FITNESS + 25 NUTRICION). La opción correcta se marca en
-- 'correcta' (A-D) y su posición varía para no ser predecible.
-- ----------------------------------------------------------------------------
INSERT INTO trivia_preguntas
    (categoria, dificultad, pregunta_es, pregunta_en,
     opcion_a_es, opcion_a_en, opcion_b_es, opcion_b_en,
     opcion_c_es, opcion_c_en, opcion_d_es, opcion_d_en, correcta) VALUES
('FITNESS', 'FACIL', '¿Qué músculo trabaja principalmente el press de banca?', 'Which muscle does the bench press mainly work?', 'Cuádriceps', 'Quadriceps', 'Pectoral mayor', 'Pectoralis major', 'Dorsal ancho', 'Latissimus dorsi', 'Gemelo', 'Calf', 'B'),
('FITNESS', 'MEDIA', '¿Cuántos músculos forman el grupo isquiotibial?', 'How many muscles form the hamstring group?', '2', '2', '4', '4', '3', '3', '1', '1', 'C'),
('FITNESS', 'FACIL', 'La sentadilla trabaja principalmente...', 'The squat mainly works...', 'Bíceps', 'Biceps', 'Tríceps', 'Triceps', 'Abdominales', 'Abs', 'Cuádriceps y glúteos', 'Quads and glutes', 'D'),
('FITNESS', 'MEDIA', '¿Qué significa 1RM en entrenamiento de fuerza?', 'What does 1RM mean in strength training?', '1 repetición máxima', '1 rep maximum', '1 minuto de reposo', '1 minute of rest', '1 ronda de músculo', '1 muscle round', '1 rutina mensual', '1 monthly routine', 'A'),
('FITNESS', 'FACIL', '¿Dónde se encuentra el bíceps braquial?', 'Where is the biceps brachii located?', 'La pierna', 'The leg', 'La espalda', 'The back', 'El brazo', 'The arm', 'El abdomen', 'The abdomen', 'C'),
('FITNESS', 'FACIL', 'Correr una maratón es un ejercicio principalmente...', 'Running a marathon is mainly a ... exercise', 'Anaeróbico', 'Anaerobic', 'Aeróbico', 'Aerobic', 'Isométrico', 'Isometric', 'De fuerza máxima', 'Max strength', 'B'),
('FITNESS', 'MEDIA', 'El peso muerto trabaja sobre todo...', 'The deadlift mainly works...', 'La cadena posterior', 'The posterior chain', 'Solo el pecho', 'Only the chest', 'Solo los brazos', 'Only the arms', 'Los gemelos', 'The calves', 'A'),
('FITNESS', 'FACIL', '¿Qué articulación se mueve en el curl de bíceps?', 'Which joint moves in a biceps curl?', 'Rodilla', 'Knee', 'Codo', 'Elbow', 'Hombro', 'Shoulder', 'Tobillo', 'Ankle', 'B'),
('FITNESS', 'MEDIA', 'La plancha (plank) es principalmente un ejercicio...', 'The plank is mainly a ... exercise', 'De salto', 'Jumping', 'Isométrico del core', 'Isometric core', 'De flexibilidad', 'Flexibility', 'Cardiovascular intenso', 'Intense cardio', 'B'),
('FITNESS', 'MEDIA', '¿Cuál es un ejercicio de tirón (pull)?', 'Which is a pulling exercise?', 'Press militar', 'Overhead press', 'Sentadilla', 'Squat', 'Dominadas', 'Pull-ups', 'Fondos', 'Dips', 'C'),
('FITNESS', 'MEDIA', 'La hipertrofia se refiere a...', 'Hypertrophy refers to...', 'Pérdida de grasa', 'Fat loss', 'Aumento del tamaño muscular', 'Muscle size increase', 'Más resistencia', 'More endurance', 'Más flexibilidad', 'More flexibility', 'B'),
('FITNESS', 'FACIL', 'El cuádriceps es un músculo de...', 'The quadriceps is a muscle of the...', 'El muslo', 'The thigh', 'El hombro', 'The shoulder', 'El cuello', 'The neck', 'El abdomen', 'The abdomen', 'A'),
('FITNESS', 'MEDIA', '¿Qué significa HIIT?', 'What does HIIT stand for?', 'Entrenamiento de baja intensidad', 'Low intensity training', 'Levantamiento internacional', 'International lifting', 'Entrenamiento de intervalos de alta intensidad', 'High intensity interval training', 'Programa de estiramientos', 'Stretching program', 'C'),
('FITNESS', 'MEDIA', 'Las dominadas con agarre prono trabajan sobre todo...', 'Pull-ups with an overhand grip mainly work...', 'El pecho', 'The chest', 'La espalda (dorsales)', 'The back (lats)', 'Los cuádriceps', 'The quads', 'Los gemelos', 'The calves', 'B'),
('FITNESS', 'MEDIA', 'Un superset consiste en...', 'A superset consists of...', 'Descansar 5 min entre series', 'Resting 5 min between sets', 'Correr antes de entrenar', 'Running before training', 'Usar siempre el peso máximo', 'Always using max weight', 'Hacer dos ejercicios seguidos sin descanso', 'Doing two exercises back to back without rest', 'D'),
('FITNESS', 'MEDIA', 'El principal músculo de la pantorrilla es el...', 'The main calf muscle is the...', 'Gastrocnemio (gemelo)', 'Gastrocnemius', 'Deltoides', 'Deltoid', 'Trapecio', 'Trapezius', 'Sartorio', 'Sartorius', 'A'),
('FITNESS', 'DIFICIL', '¿Qué es el DOMS?', 'What is DOMS?', 'Un tipo de mancuerna', 'A type of dumbbell', 'Dolor muscular de aparición tardía', 'Delayed onset muscle soreness', 'Una dieta', 'A diet', 'Un músculo', 'A muscle', 'B'),
('FITNESS', 'MEDIA', 'El press militar trabaja principalmente los...', 'The overhead press mainly works the...', 'Isquiotibiales', 'Hamstrings', 'Cuádriceps', 'Quads', 'Deltoides (hombros)', 'Deltoids (shoulders)', 'Bíceps', 'Biceps', 'C'),
('FITNESS', 'FACIL', '¿Cuál es un ejercicio de empuje (push)?', 'Which is a pushing exercise?', 'Remo con barra', 'Barbell row', 'Flexiones', 'Push-ups', 'Dominadas', 'Pull-ups', 'Curl de bíceps', 'Biceps curl', 'B'),
('FITNESS', 'FACIL', 'El "core" incluye sobre todo los músculos de...', 'The core mainly includes the muscles of the...', 'Las piernas', 'The legs', 'Los brazos', 'The arms', 'El tronco y la zona lumbar', 'The trunk and lower back', 'El cuello', 'The neck', 'C'),
('FITNESS', 'MEDIA', 'Un estiramiento estático consiste en...', 'A static stretch consists of...', 'Mantener una posición sin movimiento', 'Holding a position without movement', 'Rebotar', 'Bouncing', 'Saltar', 'Jumping', 'Correr', 'Running', 'A'),
('FITNESS', 'DIFICIL', '¿Qué mide el VO2 máx?', 'What does VO2 max measure?', 'La fuerza máxima', 'Maximum strength', 'El consumo máximo de oxígeno', 'Maximum oxygen uptake', 'La grasa corporal', 'Body fat', 'La flexibilidad', 'Flexibility', 'B'),
('FITNESS', 'MEDIA', '¿Cuál es el músculo más grande del cuerpo humano?', 'Which is the largest muscle in the human body?', 'El bíceps', 'The biceps', 'El glúteo mayor', 'The gluteus maximus', 'La lengua', 'The tongue', 'El corazón', 'The heart', 'B'),
('FITNESS', 'MEDIA', '¿Cuánto descanso mínimo se recomienda para un mismo grupo muscular?', 'What is the recommended minimum rest for the same muscle group?', '0 horas', '0 hours', '10 minutos', '10 minutes', 'Al menos 48 horas', 'At least 48 hours', '2 semanas', '2 weeks', 'C'),
('FITNESS', 'FACIL', 'Las flexiones (push-ups) trabajan sobre todo...', 'Push-ups mainly work...', 'Pecho, hombros y tríceps', 'Chest, shoulders and triceps', 'Las piernas', 'The legs', 'La espalda baja', 'The lower back', 'El cuello', 'The neck', 'A'),
('NUTRICION', 'FACIL', '¿Qué macronutriente es clave para reparar el músculo?', 'Which macronutrient is key to repairing muscle?', 'La fibra', 'Fiber', 'La proteína', 'Protein', 'El agua', 'Water', 'La vitamina C', 'Vitamin C', 'B'),
('NUTRICION', 'MEDIA', '¿Cuántas kcal aporta aprox. 1 g de proteína?', 'About how many kcal does 1 g of protein provide?', '4', '4', '9', '9', '7', '7', '2', '2', 'A'),
('NUTRICION', 'MEDIA', '¿Cuántas kcal aporta aprox. 1 g de grasa?', 'About how many kcal does 1 g of fat provide?', '4', '4', '9', '9', '7', '7', '0', '0', 'B'),
('NUTRICION', 'MEDIA', '¿Cuántas kcal aporta aprox. 1 g de carbohidrato?', 'About how many kcal does 1 g of carbohydrate provide?', '12', '12', '9', '9', '4', '4', '7', '7', 'C'),
('NUTRICION', 'FACIL', '¿Cuál de estos alimentos tiene más proteína?', 'Which of these foods has the most protein?', 'Manzana', 'Apple', 'Arroz blanco', 'White rice', 'Pechuga de pollo', 'Chicken breast', 'Aceite de oliva', 'Olive oil', 'C'),
('NUTRICION', 'FACIL', 'La principal fuente de energía rápida del cuerpo son...', 'The body''s main source of quick energy is...', 'Los carbohidratos', 'Carbohydrates', 'Las vitaminas', 'Vitamins', 'El agua', 'Water', 'El colesterol', 'Cholesterol', 'A'),
('NUTRICION', 'MEDIA', '¿Qué vitamina se sintetiza con la exposición al sol?', 'Which vitamin is synthesized through sun exposure?', 'Vitamina C', 'Vitamin C', 'Vitamina D', 'Vitamin D', 'Vitamina B12', 'Vitamin B12', 'Vitamina K', 'Vitamin K', 'B'),
('NUTRICION', 'FACIL', 'Un alimento hipercalórico tiene...', 'A high-calorie food has...', 'Muchas calorías', 'Many calories', 'Pocas calorías', 'Few calories', 'Cero calorías', 'Zero calories', 'Solo agua', 'Only water', 'A'),
('NUTRICION', 'MEDIA', '¿Cuál es un ejemplo de grasa saludable (insaturada)?', 'Which is an example of a healthy (unsaturated) fat?', 'Mantequilla', 'Butter', 'Manteca de cerdo', 'Lard', 'Aguacate', 'Avocado', 'Bollería industrial', 'Packaged pastries', 'C'),
('NUTRICION', 'MEDIA', 'La fibra dietética ayuda sobre todo a...', 'Dietary fiber mainly helps with...', 'La digestión', 'Digestion', 'Construir músculo directamente', 'Directly building muscle', 'Broncearse', 'Tanning', 'Dormir menos', 'Sleeping less', 'A'),
('NUTRICION', 'FACIL', '¿Cuál es la mejor bebida para hidratarse a diario?', 'Which is the best drink for daily hydration?', 'Refresco azucarado', 'Sugary soda', 'Alcohol', 'Alcohol', 'Bebida energética', 'Energy drink', 'Agua', 'Water', 'D'),
('NUTRICION', 'DIFICIL', '¿Qué son los BCAA?', 'What are BCAAs?', 'Aminoácidos de cadena ramificada', 'Branched-chain amino acids', 'Un tipo de grasa', 'A type of fat', 'Una vitamina', 'A vitamin', 'Un mineral', 'A mineral', 'A'),
('NUTRICION', 'MEDIA', '¿Cuál es una buena fuente de carbohidratos complejos?', 'Which is a good source of complex carbohydrates?', 'El azúcar de mesa', 'Table sugar', 'La avena', 'Oats', 'Los caramelos', 'Candy', 'El refresco', 'Soda', 'B'),
('NUTRICION', 'MEDIA', 'El exceso de calorías se almacena principalmente como...', 'Excess calories are stored mainly as...', 'Grasa', 'Fat', 'Músculo automáticamente', 'Muscle automatically', 'Agua', 'Water', 'Hueso', 'Bone', 'A'),
('NUTRICION', 'MEDIA', '¿Qué mineral es clave para transportar oxígeno en la sangre?', 'Which mineral is key to carrying oxygen in the blood?', 'Sodio', 'Sodium', 'Calcio', 'Calcium', 'Hierro', 'Iron', 'Zinc', 'Zinc', 'C'),
('NUTRICION', 'MEDIA', 'Para perder grasa suele ser necesario un...', 'To lose fat you usually need a...', 'Superávit calórico', 'Caloric surplus', 'Déficit calórico', 'Caloric deficit', 'Ayuno total', 'Total fasting', 'Exceso de sal', 'Salt excess', 'B'),
('NUTRICION', 'FACIL', '¿Cuántos vasos de agua se suelen recomendar al día como referencia?', 'How many glasses of water are commonly recommended per day as a guideline?', 'Unos 8', 'About 8', '1', '1', '20', '20', '0', '0', 'A'),
('NUTRICION', 'DIFICIL', 'La creatina se usa sobre todo para...', 'Creatine is mainly used to...', 'Broncearse', 'Tan', 'Dormir', 'Sleep', 'Mejorar fuerza y potencia', 'Improve strength and power', 'Digerir grasas', 'Digest fats', 'C'),
('NUTRICION', 'MEDIA', '¿Cuál es una fuente vegetal rica en proteína?', 'Which is a plant-based source rich in protein?', 'Las lentejas', 'Lentils', 'La lechuga', 'Lettuce', 'El pepino', 'Cucumber', 'La sandía', 'Watermelon', 'A'),
('NUTRICION', 'MEDIA', 'Los azúcares simples se caracterizan por...', 'Simple sugars are characterized by...', 'Absorberse rápido', 'Being absorbed quickly', 'No dar energía', 'Giving no energy', 'Ser proteínas', 'Being proteins', 'Ser grasas', 'Being fats', 'A'),
('NUTRICION', 'DIFICIL', '¿Qué indica el índice glucémico?', 'What does the glycemic index indicate?', 'Las calorías totales', 'Total calories', 'La velocidad a la que un alimento sube la glucosa en sangre', 'How fast a food raises blood glucose', 'La cantidad de grasa', 'The amount of fat', 'El peso del alimento', 'The weight of the food', 'B'),
('NUTRICION', 'FACIL', 'Una dieta equilibrada debe incluir...', 'A balanced diet should include...', 'Solo proteínas', 'Only protein', 'Solo carbohidratos', 'Only carbs', 'Proteínas, carbohidratos y grasas', 'Protein, carbs and fats', 'Solo grasas', 'Only fats', 'C'),
('NUTRICION', 'MEDIA', '¿Cuál de estos NO es un macronutriente?', 'Which of these is NOT a macronutrient?', 'La proteína', 'Protein', 'La grasa', 'Fat', 'El carbohidrato', 'Carbohydrate', 'La vitamina C', 'Vitamin C', 'D'),
('NUTRICION', 'MEDIA', 'La proteína de suero de leche se conoce popularmente como...', 'Milk whey protein is popularly known as...', 'Caseína', 'Casein', 'Whey', 'Whey', 'Colágeno', 'Collagen', 'Gelatina', 'Gelatin', 'B'),
('NUTRICION', 'FACIL', 'Comer proteína después de entrenar ayuda a...', 'Eating protein after training helps with...', 'La recuperación muscular', 'Muscle recovery', 'Deshidratarse', 'Getting dehydrated', 'Perder músculo', 'Losing muscle', 'Nada', 'Nothing', 'A');
