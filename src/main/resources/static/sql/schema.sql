CREATE SEQUENCE IF NOT EXISTS dog_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS dog
(
    id          integer      DEFAULT nextval('dog_id_seq') PRIMARY KEY,
    name        text NOT NULL,
    owner       text,
    description text NOT NULL
);

-- Insert sample dog data
INSERT INTO dog (name, owner, description) VALUES ('Buddy', 'John Smith', 'Friendly golden retriever, 3 years old');
INSERT INTO dog (name, owner, description) VALUES ('Max', 'Sarah Johnson', 'Energetic border collie, loves to play fetch');
INSERT INTO dog (name, owner, description) VALUES ('Bella', 'Michael Brown', 'Calm and gentle labrador, good with children');
INSERT INTO dog (name, owner, description) VALUES ('Luna', 'Emily Davis', 'Playful husky with blue eyes, enjoys long walks');
INSERT INTO dog (name, owner, description) VALUES ('Charlie', NULL, 'Rescue poodle mix, very affectionate');
INSERT INTO dog (name, owner, description) VALUES ('Lucy', 'David Wilson', 'Senior beagle, enjoys sunbathing and short walks');
INSERT INTO dog (name, owner, description) VALUES ('Cooper', NULL, 'German shepherd puppy, smart and trainable');
INSERT INTO dog (name, owner, description) VALUES ('Daisy', 'Jennifer Taylor', 'Friendly bulldog, loves belly rubs');
INSERT INTO dog (name, owner, description) VALUES ('Rocky', 'Robert Martinez', 'Athletic boxer, great running companion');
INSERT INTO dog (name, owner, description) VALUES ('Sadie', NULL, 'Gentle mixed breed, good with other pets');