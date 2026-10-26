package smoke

type Bad struct {
	Typo      string `json:"typo,omitemtpy"`
	Duplicate string `json:"first" json:"second"`
	Rule      string `validate:"min"`
	Boolean   bool   `readOnly:"yes"`
	Length    string `minLength:"-1"`
	Gorm      string `gorm:"size:large"`
}

type Good struct {
	Name string `json:"name,omitempty" binding:"required,email" conform:"trim,lower"`
	Port int    `env:"PORT,required" envDefault:"8080"`
	ID   string `bson:"_id,omitempty" dynamodbav:"id,omitempty"`
}
