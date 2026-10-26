package smoke

type WrongRules struct {
	Email     int               `validate:"email"`
	Minimum   bool              `validate:"min=1"`
	Dive      string            `validate:"dive"`
	Elements  []int             `validate:"dive,email"`
	Keys      map[int]string    `validate:"dive,keys,email,endkeys"`
	JSON      []int             `validate:"json"`
	Conform   int               `conform:"lower"`
	XML       map[string]string `xml:"field"`
	Separator int               `envSeparator:","`
	Embedded  int               `gorm:"embedded"`
	Array     int               `bun:",array"`
	InnerXML  int               `xml:",innerxml"`
}

type CorrectRules struct {
	Email    string         `validate:"required,email"`
	Number   int            `validate:"required,min=1,max=100"`
	Elements []string       `validate:"required,dive,required,email"`
	Keys     map[int]string `validate:"dive,keys,numeric,endkeys,email"`
	JSON     []byte         `validate:"json"`
	Conform  []string       `conform:"lower"`
	Ignored  func()         `json:"-"`
}
